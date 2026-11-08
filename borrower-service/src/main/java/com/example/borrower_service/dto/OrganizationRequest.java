package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record OrganizationRequest(@NotBlank @Size(max=160) String name,@NotBlank @Size(max=100) String registrationNumber,
                               @NotBlank @Pattern(regexp="INR") String currency) {}
