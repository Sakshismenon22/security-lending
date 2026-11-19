package com.example.lendora_service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.lendora_service.dto.AuthContracts.ProvisionRequest;
import com.example.lendora_service.service.WorkspaceProvisioner;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkspaceProvisionerTests {

  private final ProvisionRequest request = new ProvisionRequest(
    "LENDORA_" + "a".repeat(64),
    1000000000000L,
    "Test Org",
    "REG-1",
    "INR",
    "Admin",
    "admin@example.com"
  );

  @Test
  void missingKeyExplainsConfiguration() {
    var provisioner = new WorkspaceProvisioner(
      "http://localhost:1",
      "http://localhost:1",
      ""
    );
    var error = assertThrows(ResponseStatusException.class, () ->
      provisioner.provision("BORROWER", request)
    );
    assertEquals(503, error.getStatusCode().value());
    assertTrue(error.getReason().contains("LENDORA_PROVISIONING_KEY"));
  }

  @Test
  void unavailableBusinessServiceIsIdentified() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int port = server.getAddress().getPort();
    server.start();
    server.stop(0);
    var provisioner = new WorkspaceProvisioner(
      "http://127.0.0.1:" + port,
      "http://127.0.0.1:" + port,
      "private-key"
    );
    var error = assertThrows(ResponseStatusException.class, () ->
      provisioner.provision("BORROWER", request)
    );
    assertEquals(503, error.getStatusCode().value());
    assertTrue(error.getReason().contains("Cannot reach borrower-service"));
  }

  @Test
  void credentialsFailureIsActionableAndSuccessfulResponseReturnsOrganization() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/internal/lendora/provision", exchange -> {
      boolean authorized = "correct-key".equals(
        exchange.getRequestHeaders().getFirst("X-Lendora-Provisioning-Key")
      );
      byte[] body = (
        authorized
          ? "{\"success\":true,\"data\":{\"organizationId\":42}}"
          : "{\"success\":false}"
      ).getBytes(StandardCharsets.UTF_8);
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(authorized ? 200 : 401, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String url = "http://127.0.0.1:" + server.getAddress().getPort();
      var error = assertThrows(ResponseStatusException.class, () ->
        new WorkspaceProvisioner(url, url, "wrong-key").provision(
          "LENDER",
          request
        )
      );
      assertEquals(503, error.getStatusCode().value());
      assertTrue(error.getReason().contains("same LENDORA_PROVISIONING_KEY"));
      assertEquals(
        42L,
        new WorkspaceProvisioner(url, url, "correct-key").provision(
          "LENDER",
          request
        )
      );
    } finally {
      server.stop(0);
    }
  }
}
