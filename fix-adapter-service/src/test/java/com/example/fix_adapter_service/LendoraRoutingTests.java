package com.example.fix_adapter_service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.dto.*;
import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class LendoraRoutingTests {

  HttpServer registry;
  final java.util.concurrent.atomic.AtomicBoolean newBorrower =
    new java.util.concurrent.atomic.AtomicBoolean();

  @BeforeEach
  void startRegistry() throws Exception {
    registry = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    registry.createContext(
      "/internal/lendora/transport-organizations",
      exchange -> {
        if (
          !"test-routing-key".equals(
            exchange.getRequestHeaders().getFirst("X-Lendora-Provisioning-Key")
          )
        ) {
          exchange.sendResponseHeaders(401, -1);
          exchange.close();
          return;
        }
        var rows = new ArrayList<>(
          List.of(
            Map.of(
              "type",
              "BORROWER",
              "organizationId",
              42,
              "externalId",
              "LENDORA_BORROWER",
              "institutionId",
              "BORROWER_DEMO"
            ),
            Map.of(
              "type",
              "LENDER",
              "organizationId",
              7,
              "externalId",
              "LENDORA_LENDER",
              "institutionId",
              "LENDER_DEMO"
            ),
            Map.of(
              "type",
              "LENDER",
              "organizationId",
              8,
              "externalId",
              "LENDORA_OTHER",
              "institutionId",
              "OTHER_LENDER"
            )
          )
        );
        if (newBorrower.get()) rows.add(
          Map.of(
            "type",
            "BORROWER",
            "organizationId",
            43,
            "externalId",
            "LENDORA_NEW",
            "institutionId",
            "BORROWER_DEMO"
          )
        );
        byte[] response = MessageCodecTests.JSON.writeValueAsString(
          Map.of("success", true, "data", rows)
        ).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
      }
    );
    registry.start();
  }

  @AfterEach
  void stopRegistry() {
    registry.stop(0);
  }

  AdapterProperties properties(AdapterSide side) {
    var p = MessageCodecTests.properties(side);
    p.setLendoraRoutingEnabled(true);
    p.setLendoraKey("test-routing-key");
    p.setLendoraUrl("http://127.0.0.1:" + registry.getAddress().getPort());
    return p;
  }

  Map<String, Object> command(long org, String lender) {
    var value = new LinkedHashMap<String, Object>(
      MessageCodecTests.JSON.readValue(
        MessageCodecTests.command("registered-command"),
        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
      )
    );
    value.put("organizationId", org);
    value.put("lenderExternalId", lender);
    return value;
  }

  @Test
  void registeredBorrowerAndLogicalLenderRoundTripOverConfiguredFixSession() {
    var borrower = properties(AdapterSide.BORROWER);
    var lender = properties(AdapterSide.LENDER);
    var source = new EnvelopeService(
      borrower,
      new RouteService(borrower),
      MessageCodecTests.JSON
    );
    var target = new EnvelopeService(
      lender,
      new RouteService(lender),
      MessageCodecTests.JSON
    );
    var outbound = source
      .outbound(
        MessageCodecTests.JSON.writeValueAsString(command(42, "LENDORA_LENDER"))
      )
      .orElseThrow();
    assertEquals("FIX.4.4:BORROWER_DEMO->LENDER_DEMO", outbound.sessionKey());
    var envelope = outbound.envelope();
    var inbound = target.inbound(
      new DecodedFixMessage(
        envelope.kind(),
        envelope.eventId(),
        envelope.payloadHash(),
        envelope.payload()
      ),
      "FIX.4.4:LENDER_DEMO->BORROWER_DEMO"
    );
    assertEquals(42, inbound.envelope().organizationId());
    assertEquals(
      "LENDORA_LENDER",
      MessageCodecTests.JSON.readTree(inbound.envelope().payload())
        .path("lenderExternalId")
        .asText()
    );
    assertEquals("BORROWER_DEMO", inbound.counterpartyId());
  }

  @Test
  void confirmationsReachRegisteredBorrowerBeyondLegacyAllowlist() {
    var lender = properties(AdapterSide.LENDER);
    var borrower = properties(AdapterSide.BORROWER);
    var value = new LinkedHashMap<String, Object>();
    value.putAll(
      MessageCodecTests.JSON.readValue(
        MessageCodecTests.confirmation("registered-confirm"),
        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
      )
    );
    value.put("organizationId", 42);
    var outbound = new EnvelopeService(
      lender,
      new RouteService(lender),
      MessageCodecTests.JSON
    )
      .outbound(MessageCodecTests.JSON.writeValueAsString(value))
      .orElseThrow();
    var envelope = outbound.envelope();
    var inbound = new EnvelopeService(
      borrower,
      new RouteService(borrower),
      MessageCodecTests.JSON
    ).inbound(
      new DecodedFixMessage(
        envelope.kind(),
        envelope.eventId(),
        envelope.payloadHash(),
        envelope.payload()
      ),
      "FIX.4.4:BORROWER_DEMO->LENDER_DEMO"
    );
    assertEquals(42, inbound.envelope().organizationId());
  }

  @Test
  void unknownOrganizationsIdentitiesAndOtherInstitutionsRemainRejected() {
    var p = properties(AdapterSide.BORROWER);
    var service = new EnvelopeService(
      p,
      new RouteService(p),
      MessageCodecTests.JSON
    );
    assertThrows(IllegalArgumentException.class, () ->
      service.outbound(
        MessageCodecTests.JSON.writeValueAsString(command(99, "LENDORA_LENDER"))
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      service.outbound(
        MessageCodecTests.JSON.writeValueAsString(command(42, "UNREGISTERED"))
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      service.outbound(
        MessageCodecTests.JSON.writeValueAsString(command(42, "LENDORA_OTHER"))
      )
    );
    var lender = properties(AdapterSide.LENDER);
    assertFalse(new RouteService(lender).acceptsCommandTarget("LENDORA_OTHER"));
  }

  @Test
  void disabledModeAndUnavailablePrivateRegistryNeverWidenRoutes() {
    var disabled = MessageCodecTests.properties(AdapterSide.BORROWER);
    assertThrows(IllegalArgumentException.class, () ->
      new RouteService(disabled).counterparty("LENDER_DEMO", 42)
    );
    var p = properties(AdapterSide.BORROWER);
    p.setLendoraKey("wrong-key");
    assertThrows(IllegalArgumentException.class, () ->
      new RouteService(p).counterparty("LENDORA_LENDER", 42)
    );
  }

  @Test
  void newlyRegisteredBorrowerIsResolvedWithoutWaitingForCacheExpiry() {
    var directory = new LendoraRouteDirectory(properties(AdapterSide.BORROWER));
    assertTrue(directory.borrower(42, "BORROWER_DEMO"));
    newBorrower.set(true);
    assertTrue(directory.borrower(43, "BORROWER_DEMO"));
    assertFalse(directory.borrower(43, "OTHER_BORROWER"));
  }
}
