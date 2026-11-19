package com.example.borrower_service.controller;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.model.Organization;
import com.example.borrower_service.model.User;
import com.example.borrower_service.repository.*;
import com.example.borrower_service.response.ApiResponse;
import com.example.borrower_service.service.DirectoryService;
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
  private final BorrowerRepository db;

  public LendoraProvisioningController(
    DirectoryService directory,
    BorrowerRepository db,
    @Value("${lendora.provisioning-key:}") String key
  ) {
    this.directory = directory;
    this.db = db;
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
    var existing = db.rows(
      Organization.class,
      eq("lendoraExternalKey", r.externalKey())
    );
    long org;
    if (existing.isEmpty()) {
      org = n(
        directory.organization(
          new OrganizationRequest(
            r.name(),
            r.registrationNumber(),
            r.currency()
          )
        ),
        "id"
      );
      db.edit(Organization.class, org, o ->
        o.setLendoraExternalKey(r.externalKey())
      );
    } else org = n(existing.get(0), "id");
    db.lock(org);
    var members = db.rows(User.class, eq("organizationId", org));
    var admin = members
      .stream()
      .filter(u ->
        String.valueOf(u.get("email")).equalsIgnoreCase(r.administratorEmail())
      )
      .findFirst()
      .orElse(null);
    if (admin == null && !members.isEmpty()) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Provisioning identity does not match the first administrator"
    );
    if (admin == null) admin = directory.member(
      org,
      new MemberRequest(r.administratorName(), r.administratorEmail(), "ADMIN")
    );
    return ApiResponse.ok(
      Map.of("organizationId", org, "administratorId", n(admin, "id"))
    );
  }
}
