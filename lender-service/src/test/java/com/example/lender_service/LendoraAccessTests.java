package com.example.lender_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lender_service.service.DirectoryService;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "lendora.auth-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LendoraAccessTests {

  static final HttpServer server;
  static final AtomicLong ownOrg = new AtomicLong();
  static final java.util.concurrent.atomic.AtomicReference<String> memberEmail =
    new java.util.concurrent.atomic.AtomicReference<>("");

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/api/v1/auth/me", exchange -> {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        boolean wrong = cookie != null && cookie.contains("B".repeat(43));
        String role = wrong ? "WRONG" : "LENDER";
        String json =
          "{\"success\":true,\"data\":{\"organization\":{\"type\":\"" +
          role +
          "\",\"platformOrganizationId\":" +
          ownOrg.get() +
          "},\"user\":{\"email\":\"" +
          memberEmail.get() +
          "\",\"role\":\"ADMIN\"}}}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void endpoint(DynamicPropertyRegistry registry) {
    registry.add(
      "lendora.auth-url",
      () -> "http://127.0.0.1:" + server.getAddress().getPort()
    );
  }

  @AfterAll
  static void close() {
    server.stop(0);
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  DirectoryService directory;

  final JsonMapper json = JsonMapper.builder().build();

  long organization() {
    var organization = directory.create(
      new com.example.lender_service.dto.OrganizationRequest(
        "Auth tenant " + UUID.randomUUID(),
        "AUTH_" + UUID.randomUUID(),
        System.nanoTime()
      )
    );
    return organization.getId();
  }

  @Autowired
  com.example.lender_service.service.MarketService market;

  @Autowired
  com.example.lender_service.service.InventoryService inventory;

  @Autowired
  com.example.lender_service.repository.UserRepository members;

  @Test
  void inventoryReceiptsAreAdminOnlyIdempotentAndCannotBeEnabledByClaimedRoles() throws Exception {
    long id = organization();
    ownOrg.set(id);
    String suffix = UUID.randomUUID().toString();
    String adminEmail = "admin-" + suffix + "@example.com",
      readerEmail = "reader-" + suffix + "@example.com";
    var admin = directory.member(
      id,
      new com.example.lender_service.dto.MemberRequest(
        "Admin",
        adminEmail,
        "ADMIN"
      )
    );
    var reader = directory.member(
      id,
      new com.example.lender_service.dto.MemberRequest(
        "Reader",
        readerEmail,
        "READ_ONLY"
      )
    );
    long security = System.nanoTime();
    market.register(
      new com.example.lender_service.dto.SecurityRequest(
        security,
        "EQADMIN",
        "NSE",
        "Admin equity",
        "INADMIN",
        "INR"
      )
    );
    String body = json.writeValueAsString(
      Map.of("securityId", security, "quantity", 100, "reference", suffix)
    );
    String path = "/api/v1/organizations/" + id;
    var cookie = new Cookie("LENDORA_SESSION", "A".repeat(43));
    memberEmail.set(readerEmail);
    mvc
      .perform(get(path + "/admin-access").cookie(cookie))
      .andExpect(jsonPath("$.data.admin").value(false));
    mvc
      .perform(
        post(path + "/inventory/receipts")
          .cookie(cookie)
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post(path + "/inventory")
          .cookie(cookie)
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        patch(path + "/users/" + reader.getId())
          .cookie(cookie)
          .contentType("application/json")
          .content("{\"role\":\"ADMIN\",\"active\":true}")
      )
      .andExpect(status().isForbidden());
    memberEmail.set(adminEmail);
    mvc
      .perform(get(path + "/admin-access").cookie(cookie))
      .andExpect(jsonPath("$.data.admin").value(true));
    for (int i = 0; i < 2; i++) mvc
      .perform(
        post(path + "/inventory/receipts")
          .cookie(cookie)
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isOk());
    assertEquals(100, inventory.position(id, security).getTotalQuantity());
    String next = json.writeValueAsString(
      Map.of(
        "securityId",
        security,
        "quantity",
        20,
        "reference",
        suffix + "-next"
      )
    );
    mvc
      .perform(
        post(path + "/inventory/receipts")
          .cookie(cookie)
          .contentType("application/json")
          .content(next)
      )
      .andExpect(status().isOk());
    assertEquals(120, inventory.position(id, security).getAvailableQuantity());
    mvc
      .perform(
        post(path + "/inventory/receipts")
          .cookie(cookie)
          .contentType("application/json")
          .content(
            json.writeValueAsString(
              Map.of(
                "securityId",
                security,
                "quantity",
                101,
                "reference",
                suffix
              )
            )
          )
      )
      .andExpect(status().is4xxClientError());
    admin.setActive(false);
    members.save(admin);
    mvc
      .perform(get(path + "/admin-access").cookie(cookie))
      .andExpect(jsonPath("$.data.admin").value(false));
    mvc
      .perform(
        post(path + "/inventory/receipts")
          .cookie(cookie)
          .contentType("application/json")
          .content(next)
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void workspaceRoleAndOrganizationCannotBeChangedByTheClient() throws Exception {
    long id = organization(),
      other = organization();
    ownOrg.set(id);
    var cookie = new Cookie("LENDORA_SESSION", "A".repeat(43));
    mvc
      .perform(get("/api/v1/organizations/" + id + "/users"))
      .andExpect(status().isUnauthorized());
    mvc
      .perform(get("/api/v1/organizations/" + id + "/users").cookie(cookie))
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/v1/organizations/" + other + "/users").cookie(cookie))
      .andExpect(status().isForbidden());
    mvc
      .perform(get("/api/v1/organizations/+" + other + "/users").cookie(cookie))
      .andExpect(status().isForbidden());
    mvc
      .perform(
        get("/api/v1/organizations/" + id + "/users").cookie(
          new Cookie("LENDORA_SESSION", "B".repeat(43))
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(get("/api/v1/organizations").cookie(cookie))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(1))
      .andExpect(jsonPath("$.data[0].id").value(id));
  }

  @Test
  void provisioningIsPrivateAndIdempotent() throws Exception {
    String key =
      "LENDORA_" + UUID.randomUUID().toString().replace("-", "").repeat(2);
    String request = json.writeValueAsString(
      Map.of(
        "externalKey",
        key,
        "publicReferenceId",
        System.nanoTime(),
        "name",
        "Provisioned tenant",
        "registrationNumber",
        key,
        "currency",
        "INR",
        "administratorName",
        "First Admin",
        "administratorEmail",
        "admin-" + UUID.randomUUID() + "@example.com"
      )
    );
    mvc
      .perform(
        post("/internal/lendora/provision")
          .contentType("application/json")
          .content(request)
      )
      .andExpect(status().isUnauthorized());
    var first = mvc
      .perform(
        post("/internal/lendora/provision")
          .header("X-Lendora-Provisioning-Key", "test-provisioning-key")
          .contentType("application/json")
          .content(request)
      )
      .andExpect(status().isOk())
      .andReturn();
    var second = mvc
      .perform(
        post("/internal/lendora/provision")
          .header("X-Lendora-Provisioning-Key", "test-provisioning-key")
          .contentType("application/json")
          .content(request)
      )
      .andExpect(status().isOk())
      .andReturn();
    assertEquals(
      json.readTree(first.getResponse().getContentAsString()).path("data"),
      json.readTree(second.getResponse().getContentAsString()).path("data")
    );
  }
}
