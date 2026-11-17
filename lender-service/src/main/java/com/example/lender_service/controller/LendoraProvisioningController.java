package com.example.lender_service.controller;

import com.example.lender_service.dto.*;
import com.example.lender_service.repository.*;
import com.example.lender_service.response.ApiResponse;
import com.example.lender_service.service.DirectoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/lendora")
public class LendoraProvisioningController {

  private final DirectoryService directory;
  private final String key;
  private final OrganizationRepository organizations;
  private final UserRepository users;

  public LendoraProvisioningController(
    DirectoryService directory,
    OrganizationRepository organizations,
    UserRepository users,
    @Value("${lendora.provisioning-key:}") String key
  ) {
    this.directory = directory;
    this.organizations = organizations;
    this.users = users;
    this.key = key;
  }

  public record ProvisionRequest(
    @NotNull @Pattern(regexp = "LENDORA_[a-f0-9]{64}") String externalKey,
    @Positive long publicReferenceId,
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 100) String registrationNumber,
    @NotNull @Pattern(regexp = "INR") String currency,
    @NotBlank @Size(max = 160) String administratorName,
    @NotBlank @Email @Size(max = 254) String administratorEmail
  ) {}

  @PostMapping("/provision")
  @Transactional
  public ApiResponse<?> provision(
    @RequestHeader(
      name = "X-Lendora-Provisioning-Key",
      required = false
    ) String provided,
    @Valid @RequestBody ProvisionRequest r
  ) {
    if (key.isBlank()) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Lendora provisioning is not configured"
    );
    if (
      provided == null ||
      !MessageDigest.isEqual(
        key.getBytes(StandardCharsets.UTF_8),
        provided.getBytes(StandardCharsets.UTF_8)
      )
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Invalid provisioning credentials"
    );
    var organization = organizations
      .findByExternalId(r.externalKey())
      .orElseGet(() ->
        directory.create(
          new OrganizationRequest(
            r.name(),
            r.externalKey(),
            r.publicReferenceId()
          )
        )
      );
    var members = users
      .findByOrganizationId(
        organization.getId(),
        org.springframework.data.domain.Pageable.unpaged()
      )
      .getContent();
    var admin = members
      .stream()
      .filter(u -> u.getEmail().equalsIgnoreCase(r.administratorEmail()))
      .findFirst()
      .orElse(null);
    if (admin == null && !members.isEmpty()) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Provisioning identity does not match the first administrator"
    );
    if (admin == null) admin = directory.member(
      organization.getId(),
      new MemberRequest(r.administratorName(), r.administratorEmail(), "ADMIN")
    );
    return ApiResponse.ok(
      Map.of(
        "organizationId",
        organization.getId(),
        "administratorId",
        admin.getId()
      )
    );
  }
}
