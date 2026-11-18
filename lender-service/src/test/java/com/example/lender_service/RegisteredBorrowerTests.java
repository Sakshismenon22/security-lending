package com.example.lender_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lender_service.dto.OrganizationRequest;
import com.example.lender_service.repository.*;
import com.example.lender_service.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegisteredBorrowerTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  DirectoryService directory;

  @Autowired
  BorrowerConnectionRepository connections;

  @Autowired
  OutboxEventRepository outbox;

  @MockitoBean
  RegisteredBorrowerClient registry;

  final RegisteredBorrowerClient.BorrowerEntry borrower =
    new RegisteredBorrowerClient.BorrowerEntry(
      1000000000001L,
      77,
      "Registered borrower",
      "REG-77",
      "India",
      "INR",
      "BORROWER"
    );

  @BeforeEach
  void registry() {
    when(registry.list()).thenReturn(List.of(borrower));
    when(registry.find(borrower.directoryId())).thenReturn(borrower);
  }

  long organization() {
    return directory
      .create(
        new OrganizationRequest(
          "Directory test lender",
          "LENDER_" + UUID.randomUUID(),
          123456
        )
      )
      .getId();
  }

  String path(long org) {
    return "/api/v1/organizations/" + org + "/borrower-directory";
  }

  @Test
  void directorySelectionUsesVerifiedDetailsAndDuplicatePostsDoNotQueueAgain() throws Exception {
    long org = organization();
    long before = outbox.count();
    mvc
      .perform(get(path(org)))
      .andExpect(status().isOk())
      .andExpect(
        jsonPath("$.data.content[0].directoryId").value(borrower.directoryId())
      );
    String body = "{\"directoryId\":" + borrower.directoryId() + "}";
    mvc
      .perform(
        post(path(org) + "/connections")
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("Registered borrower"))
      .andExpect(jsonPath("$.data.borrowerOrganizationId").value(77))
      .andExpect(jsonPath("$.data.externalId").value("BORROWER_DEMO"));
    assertEquals(1, connections.findByOrganizationId(org).size());
    assertEquals(before + 1, outbox.count());
    mvc
      .perform(get(path(org)))
      .andExpect(jsonPath("$.data.content.length()").value(0))
      .andExpect(jsonPath("$.data.alreadyConnected").value(1));
    mvc
      .perform(
        post(path(org) + "/connections")
          .contentType("application/json")
          .content(body)
      )
      .andExpect(status().isOk());
    assertEquals(1, connections.findByOrganizationId(org).size());
    assertEquals(before + 1, outbox.count());
  }

  @Test
  void availabilityIsPerLenderAndInactiveConnectionsAreNotDuplicated() throws Exception {
    long first = organization(),
      second = directory
        .create(
          new OrganizationRequest(
            "Other lender",
            "OTHER_" + UUID.randomUUID(),
            123457
          )
        )
        .getId();
    var existing = directory.connect(
      first,
      new com.example.lender_service.dto.BorrowerConnectionRequest(
        "BORROWER_DEMO",
        77,
        "Existing borrower"
      )
    );
    existing.setActive(false);
    connections.saveAndFlush(existing);
    mvc
      .perform(get(path(first)))
      .andExpect(jsonPath("$.data.content.length()").value(0));
    mvc
      .perform(get(path(second)))
      .andExpect(jsonPath("$.data.content.length()").value(1));
    mvc
      .perform(
        post(path(first) + "/connections")
          .contentType("application/json")
          .content("{\"directoryId\":" + borrower.directoryId() + "}")
      )
      .andExpect(status().isConflict());
    assertEquals(1, connections.findByOrganizationId(first).size());
  }

  @Test
  void invalidSelectionsAndDirectoryOutagesDoNotCreateConnections() throws Exception {
    long org = organization();
    when(registry.find(999)).thenThrow(
      new ResponseStatusException(HttpStatus.NOT_FOUND, "Not available")
    );
    mvc
      .perform(
        post(path(org) + "/connections")
          .contentType("application/json")
          .content("{\"directoryId\":999}")
      )
      .andExpect(status().isNotFound());
    mvc
      .perform(
        post(path(org) + "/connections")
          .contentType("application/json")
          .content("{\"directoryId\":0}")
      )
      .andExpect(status().isBadRequest());
    when(registry.list()).thenThrow(
      new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Directory offline"
      )
    );
    mvc.perform(get(path(org))).andExpect(status().isServiceUnavailable());
    assertTrue(connections.findByOrganizationId(org).isEmpty());
  }
}
