package com.example.lendora_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class AuthContracts {

  private AuthContracts() {}

  public record OrganizationInput(
    @NotBlank @Size(max = 160) String name,
    @NotNull @Pattern(regexp = "LENDER|BORROWER") String type,
    @NotBlank @Size(max = 100) String registrationNumber,
    @NotBlank @Size(max = 80) String country,
    @NotNull
    @Pattern(
      regexp = "INR",
      message = "Only INR workspaces are currently supported"
    )
    String baseCurrency,
    @NotBlank @Size(max = 500) String registeredAddress
  ) {}

  public record AdministratorInput(
    @NotBlank @Size(max = 160) String fullName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 256) String password
  ) {}

  public record Registration(
    @NotNull @Valid OrganizationInput organization,
    @NotNull @Valid AdministratorInput administrator,
    @AssertTrue(message = "Terms must be accepted") boolean acceptedTerms
  ) {}

  public record Login(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 256) String password,
    Boolean rememberMe
  ) {}

  public record OrganizationView(
    Long id,
    String name,
    String type,
    String registrationNumber,
    String country,
    String baseCurrency,
    String registeredAddress,
    Long platformOrganizationId
  ) {}

  public record UserView(Long id, String fullName, String email, String role) {}

  public record Identity(
    OrganizationView organization,
    UserView user,
    String workspace
  ) {}

  public record LoginResult(
    String token,
    Identity identity,
    long maxAgeSeconds
  ) {}

  public record ProvisionRequest(
    String externalKey,
    long publicReferenceId,
    String name,
    String registrationNumber,
    String currency,
    String administratorName,
    String administratorEmail
  ) {}
}
