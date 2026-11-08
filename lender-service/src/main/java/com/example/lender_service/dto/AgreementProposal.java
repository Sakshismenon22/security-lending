package com.example.lender_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record AgreementProposal(
    @Positive long lenderId,
    @NotBlank @Size(max = 100) String number,
    @NotNull LocalDate effectiveDate,
    @NotNull LocalDate expiryDate,
    @NotNull @DecimalMin("0.000001") BigDecimal creditLimit,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal feeRate,
    @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rebateRate,
    @NotNull @DecimalMin("1") @DecimalMax("5") BigDecimal coverageRatio,
    @NotNull @Pattern(regexp = "MONTHLY|AT_CLOSE") String billingFrequency,
    @Min(360) @Max(365) int dayBasis,
    boolean recallAllowed,
    @Min(1) int marginHours,
    @Min(1) int recallHours,
    @NotEmpty List<@Valid RuleRequest> collateralRules) {}
