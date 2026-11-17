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
}
