package com.example.lender_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.lender_service.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DefaultScanClientTests {

  @Test
  void scanDelegatesOnlyTheVerifiedTenantWithPrivateCredentialsAndAuditsCompletion() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var key = new AtomicReference<String>();
    server.createContext(
      "/internal/lifecycle/organizations/17/defaults/scan",
      exchange -> {
        key.set(
          exchange.getRequestHeaders().getFirst("X-Lendora-Provisioning-Key")
        );
        byte[] body =
          "{\"success\":true,\"data\":{\"organizationId\":17,\"loansChecked\":3,\"openCases\":1,\"loansFailed\":0}}".getBytes(
            StandardCharsets.UTF_8
          );
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      }
    );
    server.start();
    try {
      var tenants = mock(TenantService.class);
      var events = mock(EventService.class);
      var scans = new DefaultScanService(
        tenants,
        events,
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "test-private"
      );
      var result = scans.run(17);
      assertEquals(3, result.path("loansChecked").asInt());
      assertEquals("test-private", key.get());
      verify(tenants).exists(17);
      verify(events).local(17, "DEFAULT_SCAN_COMPLETED", 17, result);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void missingServiceCredentialsFailClosed() {
    var tenants = mock(TenantService.class);
    var events = mock(EventService.class);
    var scans = new DefaultScanService(
      tenants,
      events,
      "http://127.0.0.1:1",
      ""
    );
    assertEquals(
      503,
      assertThrows(ResponseStatusException.class, () -> scans.run(17))
        .getStatusCode()
        .value()
    );
    verifyNoInteractions(events);
  }
}
