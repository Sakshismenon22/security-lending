package com.example.lendora_service.controller;

import com.example.lendora_service.repository.AccountOrganizationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/lendora/transport-organizations")
@Transactional(readOnly = true)
public class TransportDirectoryController {

  private final AccountOrganizationRepository organizations;
  private final String key, lenderInstitution, borrowerInstitution;

  public TransportDirectoryController(
    AccountOrganizationRepository organizations,
    @Value("${lendora.provisioning-key:}") String key,
    @Value(
      "${lendora.lender-institution-id:LENDER_DEMO}"
    ) String lenderInstitution,
    @Value(
      "${lendora.borrower-institution-id:BORROWER_DEMO}"
    ) String borrowerInstitution
  ) {
    this.organizations = organizations;
    this.key = key;
    this.lenderInstitution = lenderInstitution;
    this.borrowerInstitution = borrowerInstitution;
  }

  public record TransportOrganization(
    String type,
    Long organizationId,
    String externalId,
    String institutionId
  ) {}

  @GetMapping
  public Map<String, Object> list(
    @RequestHeader(
      name = "X-Lendora-Provisioning-Key",
      required = false
    ) String provided
  ) {
    if (key.isBlank()) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Lendora transport directory is not configured"
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
    var entries = organizations
      .findByPlatformOrganizationIdIsNotNullOrderByIdAsc()
      .stream()
      .map(o ->
        new TransportOrganization(
          o.getType(),
          o.getPlatformOrganizationId(),
          o.getExternalKey(),
          "LENDER".equals(o.getType()) ? lenderInstitution : borrowerInstitution
        )
      )
      .toList();
    return Map.of("success", true, "data", entries);
  }
}
