package com.example.lendora_service.controller;

import com.example.lendora_service.model.AccountOrganization;
import com.example.lendora_service.repository.AccountOrganizationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Minimal organization directory for trusted business services; no user credentials or contacts. */
@RestController
@RequestMapping("/internal/lendora/borrowers")
@Transactional(readOnly = true)
public class BorrowerDirectoryController {

  private final AccountOrganizationRepository organizations;
  private final String key;

  public BorrowerDirectoryController(
    AccountOrganizationRepository organizations,
    @Value("${lendora.provisioning-key:}") String key
  ) {
    this.organizations = organizations;
    this.key = key;
  }

  public record BorrowerEntry(
    Long directoryId,
    Long borrowerOrganizationId,
    String name,
    String registrationNumber,
    String country,
    String currency,
    String type
  ) {}

  private void authorize(String provided) {
    if (key.isBlank()) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Borrower directory is not configured"
    );
    if (
      provided == null ||
      !MessageDigest.isEqual(
        key.getBytes(StandardCharsets.UTF_8),
        provided.getBytes(StandardCharsets.UTF_8)
      )
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Invalid service credentials"
    );
  }

  private BorrowerEntry entry(AccountOrganization organization) {
    return new BorrowerEntry(
      organization.getId(),
      organization.getPlatformOrganizationId(),
      organization.getName(),
      organization.getRegistrationNumber(),
      organization.getCountry(),
      organization.getBaseCurrency(),
      organization.getType()
    );
  }

  @GetMapping
  public Map<String, Object> list(
    @RequestHeader(
      name = "X-Lendora-Provisioning-Key",
      required = false
    ) String provided
  ) {
    authorize(provided);
    return Map.of(
      "success",
      true,
      "data",
      organizations
        .findByTypeAndPlatformOrganizationIdIsNotNullOrderByNameAsc("BORROWER")
        .stream()
        .map(this::entry)
        .toList()
    );
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(
    @RequestHeader(
      name = "X-Lendora-Provisioning-Key",
      required = false
    ) String provided,
    @PathVariable long id
  ) {
    authorize(provided);
    var organization = organizations
      .findById(id)
      .filter(
        o ->
          "BORROWER".equals(o.getType()) &&
          o.getPlatformOrganizationId() != null
      )
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Registered borrower is not available"
        )
      );
    return Map.of("success", true, "data", entry(organization));
  }
}
