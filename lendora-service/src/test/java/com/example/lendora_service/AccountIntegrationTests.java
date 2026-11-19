package com.example.lendora_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lendora_service.repository.*;
import com.example.lendora_service.service.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  AccountUserRepository users;

  @Autowired
  AccountOrganizationRepository organizations;

  @Autowired
  AccountSessionRepository sessions;

  @MockitoBean
  WorkspaceProvisioner provisioner;

  final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void setup() {
    sessions.deleteAll();
    users.deleteAll();
    organizations.deleteAll();
    when(provisioner.provision(anyString(), any())).thenReturn(42L);
  }

  String registration(String type, String email) {
    return json.writeValueAsString(
      Map.of(
        "organization",
        Map.of(
          "name",
          "Example Capital",
          "type",
          type,
          "registrationNumber",
          "REG-" + type,
          "country",
          "India",
          "baseCurrency",
          "INR",
          "registeredAddress",
          "100 Market Road"
        ),
        "administrator",
        Map.of(
          "fullName",
          "Example Admin",
          "email",
          email,
          "password",
          "valid-passphrase-2026"
        ),
        "acceptedTerms",
        true
      )
    );
  }

  Cookie login(String email, boolean remember) throws Exception {
    var result = mvc
      .perform(
        post("/api/v1/auth/login")
          .contentType("application/json")
          .content(
            json.writeValueAsString(
              Map.of(
                "email",
                email,
                "password",
                "valid-passphrase-2026",
                "rememberMe",
                remember
              )
            )
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
      .andReturn();
    String header = result.getResponse().getHeader("Set-Cookie");
    assertNotNull(header);
    assertTrue(header.contains("HttpOnly"));
    assertTrue(header.contains("SameSite=Lax"));
    assertEquals(remember, header.contains("Max-Age="));
    return new Cookie(
      "LENDORA_SESSION",
      header.substring(header.indexOf('=') + 1, header.indexOf(';'))
    );
  }

  @Test
  void signupLoginMeAndLogoutUseTheStoredOrganizationType() throws Exception {
    for (String type : List.of("LENDER", "BORROWER")) {
      String email = type.toLowerCase() + "@example.com";
      mvc
        .perform(
          post("/api/v1/organizations/register")
            .contentType("application/json")
            .content(registration(type, email))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.organization.type").value(type));
      var cookie = login(email, false);
      mvc
        .perform(get("/api/v1/auth/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspace").value(type))
        .andExpect(jsonPath("$.data.user.email").value(email));
      assertTrue(
        sessions.findById(AccountService.digest(cookie.getValue())).isPresent()
      );
      mvc
        .perform(post("/api/v1/auth/logout").cookie(cookie))
        .andExpect(status().isOk());
      mvc
        .perform(get("/api/v1/auth/me").cookie(cookie))
        .andExpect(status().isUnauthorized());
    }
    assertEquals(2, users.count());
    assertTrue(
      users
        .findAll()
        .stream()
        .allMatch(u -> u.getPasswordHash().startsWith("pbkdf2_sha256$600000$"))
    );
    verify(provisioner, times(2)).provision(anyString(), any());
  }

  @Test
  void duplicateCaseInsensitiveEmailAndInvalidRegistrationAreRejected() throws Exception {
    mvc
      .perform(
        post("/api/v1/organizations/register")
          .contentType("application/json")
          .content(registration("LENDER", "ADMIN@example.com"))
      )
      .andExpect(status().isCreated());
    mvc
      .perform(
        post("/api/v1/organizations/register")
          .contentType("application/json")
          .content(registration("BORROWER", "admin@example.com"))
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        post("/api/v1/organizations/register")
          .contentType("application/json")
          .content(registration("INVALID", "other@example.com"))
      )
      .andExpect(status().isBadRequest());
    assertEquals(1, organizations.count());
    assertEquals(1, users.count());
  }

  @Test
  void invalidCredentialsLockOutAndLoginCannotOverrideStoredRole() throws Exception {
    mvc
      .perform(
        post("/api/v1/organizations/register")
          .contentType("application/json")
          .content(registration("BORROWER", "admin@example.com"))
      )
      .andExpect(status().isCreated());
    var spoofed = mvc
      .perform(
        post("/api/v1/auth/login")
          .contentType("application/json")
          .content(
            "{\"email\":\"admin@example.com\",\"password\":\"valid-passphrase-2026\",\"workspace\":\"LENDER\"}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.workspace").value("BORROWER"))
      .andReturn();
    mvc
      .perform(
        post("/api/v1/auth/logout").cookie(
          spoofed.getResponse().getCookie("LENDORA_SESSION")
        )
      )
      .andExpect(status().isOk());
    for (int i = 0; i < 5; i++) mvc
      .perform(
        post("/api/v1/auth/login")
          .contentType("application/json")
          .content("{\"email\":\"admin@example.com\",\"password\":\"wrong\"}")
      )
      .andExpect(status().isUnauthorized())
      .andExpect(
        jsonPath("$.message").value("Email or password is incorrect.")
      );
    assertEquals(0, sessions.count());
    assertNotNull(users.findAll().get(0).getLockedUntil());
    mvc
      .perform(
        post("/api/v1/auth/login")
          .contentType("application/json")
          .content(
            "{\"email\":\"admin@example.com\",\"password\":\"valid-passphrase-2026\"}"
          )
      )
      .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void provisioningFailureRollsBackAndRememberedSessionIsRevokedOnRotation() throws Exception {
    when(provisioner.provision(anyString(), any())).thenThrow(
      new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "Workspace unavailable"
      )
    );
    mvc
      .perform(
        post("/api/v1/organizations/register")
          .contentType("application/json")
          .content(registration("LENDER", "admin@example.com"))
      )
      .andExpect(status().isServiceUnavailable());
    assertEquals(0, users.count());
    assertEquals(0, organizations.count());
    doReturn(42L).when(provisioner).provision(anyString(), any());
    mvc
      .perform(
        post("/api/v1/organizations/register")
          .contentType("application/json")
          .content(registration("LENDER", "admin@example.com"))
      )
      .andExpect(status().isCreated());
    var original = login("admin@example.com", true);
    mvc
      .perform(
        post("/api/v1/auth/login")
          .cookie(original)
          .contentType("application/json")
          .content(
            "{\"email\":\"admin@example.com\",\"password\":\"valid-passphrase-2026\"}"
          )
      )
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/v1/auth/me").cookie(original))
      .andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/v1/auth/logout").header(
          "Origin",
          "https://untrusted.example"
        )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void privateBorrowerDirectoryListsOnlyProvisionedBorrowersWithoutAccountSecrets() throws Exception {
    mvc.perform(post("/api/v1/organizations/register").contentType("application/json").content(registration("BORROWER", "borrower@example.com"))).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/organizations/register").contentType("application/json").content(registration("LENDER", "lender@example.com"))).andExpect(status().isCreated());
    var borrower = organizations.findAll().stream().filter(o -> "BORROWER".equals(o.getType())).findFirst().orElseThrow();
    var lender = organizations.findAll().stream().filter(o -> "LENDER".equals(o.getType())).findFirst().orElseThrow();
    mvc.perform(get("/internal/lendora/borrowers")).andExpect(status().isUnauthorized());
    mvc.perform(get("/internal/lendora/borrowers").header("X-Lendora-Provisioning-Key", "wrong-key")).andExpect(status().isUnauthorized());
    mvc.perform(get("/internal/lendora/borrowers").header("X-Lendora-Provisioning-Key", "test-provisioning-key"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].directoryId").value(borrower.getId()))
        .andExpect(jsonPath("$.data[0].borrowerOrganizationId").value(42))
        .andExpect(jsonPath("$.data[0].type").value("BORROWER"))
        .andExpect(jsonPath("$.data[0].email").doesNotExist())
        .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data[0].administrator").doesNotExist());
    mvc.perform(get("/internal/lendora/borrowers/" + lender.getId()).header("X-Lendora-Provisioning-Key", "test-provisioning-key")).andExpect(status().isNotFound());
    mvc.perform(get("/internal/lendora/borrowers/" + borrower.getId()).header("X-Lendora-Provisioning-Key", "test-provisioning-key"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.registrationNumber").value("REG-BORROWER"));
    borrower.setPlatformOrganizationId(null); organizations.saveAndFlush(borrower);
    mvc.perform(get("/internal/lendora/borrowers").header("X-Lendora-Provisioning-Key", "test-provisioning-key"))
        .andExpect(jsonPath("$.data.length()").value(0));
    mvc.perform(get("/internal/lendora/borrowers/" + borrower.getId()).header("X-Lendora-Provisioning-Key", "test-provisioning-key")).andExpect(status().isNotFound());
  }

  @Test void privateTransportRegistrySeparatesBusinessIdsFromFixInstitutionIds() throws Exception {
    mvc.perform(post("/api/v1/organizations/register").contentType("application/json").content(registration("LENDER", "lender@transport.example"))).andExpect(status().isCreated());
    mvc.perform(post("/api/v1/organizations/register").contentType("application/json").content(registration("BORROWER", "borrower@transport.example"))).andExpect(status().isCreated());
    mvc.perform(get("/internal/lendora/transport-organizations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/internal/lendora/transport-organizations").header("X-Lendora-Provisioning-Key", "test-provisioning-key"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
      .andExpect(jsonPath("$.data[0].type").value("LENDER"))
      .andExpect(jsonPath("$.data[0].institutionId").value("LENDER_DEMO"))
      .andExpect(jsonPath("$.data[1].institutionId").value("BORROWER_DEMO"))
      .andExpect(jsonPath("$.data[0].organizationId").value(42))
      .andExpect(jsonPath("$.data[0].email").doesNotExist());
  }
}
