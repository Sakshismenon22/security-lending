package com.example.lendora_service.service;

import com.example.lendora_service.dto.AuthContracts.ProvisionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class WorkspaceProvisioner {

  private final String lender, borrower, key;

  public WorkspaceProvisioner(
    @Value("${lendora.lender-url}") String lender,
    @Value("${lendora.borrower-url}") String borrower,
    @Value("${lendora.provisioning-key:}") String key
  ) {
    this.lender = lender;
    this.borrower = borrower;
    this.key = key;
  }

  public long provision(String type, ProvisionRequest request) {
    if (key.isBlank()) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Organization registration is not configured. Contact the platform administrator."
    );
    var factory =
      new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(10000);
    try {
      var response = RestClient.builder()
        .baseUrl(type.equals("BORROWER") ? borrower : lender)
        .requestFactory(factory)
        .build()
        .post()
        .uri("/internal/lendora/provision")
        .header("X-Lendora-Provisioning-Key", key)
        .body(request)
        .retrieve()
        .body(JsonNode.class);
      if (
        response == null ||
        !response.path("success").asBoolean() ||
        response.path("data").path("organizationId").asLong() <= 0
      ) throw new IllegalStateException(
        "Invalid workspace provisioning response"
      );
      return response.path("data").path("organizationId").asLong();
    } catch (Exception e) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Your workspace could not be created. Please try again later.",
        e
      );
    }
  }
}
