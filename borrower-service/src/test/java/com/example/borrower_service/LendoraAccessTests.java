package com.example.borrower_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.borrower_service.service.DirectoryService;
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

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/api/v1/auth/me", exchange -> {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        boolean wrong = cookie != null && cookie.contains("B".repeat(43));
        String role = wrong ? "WRONG" : "BORROWER";
        String json =
          "{\"success\":true,\"data\":{\"organization\":{\"type\":\"" +
          role +
          "\",\"platformOrganizationId\":" +
          ownOrg.get() +
          "}}}";
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
    var organization = directory.organization(
      new com.example.borrower_service.dto.OrganizationRequest(
        "Auth tenant " + UUID.randomUUID(),
        "AUTH_" + UUID.randomUUID(),
        "INR"
      )
    );
    return ((Number) organization.get("id")).longValue();
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
