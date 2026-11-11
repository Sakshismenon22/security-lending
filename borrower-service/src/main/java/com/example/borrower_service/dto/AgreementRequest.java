package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record AgreementRequest(@Positive long lenderId,@NotBlank @Size(max=100) String number,@NotNull LocalDate effectiveDate,
        @NotNull LocalDate expiryDate,@NotNull @DecimalMin("0.000001") BigDecimal creditLimit,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal feeRate,@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rebateRate,
        @NotNull @DecimalMin("1") @DecimalMax("5") BigDecimal coverageRatio,
        @NotNull @Pattern(regexp="MONTHLY|AT_CLOSE") String billingFrequency,@Min(360) @Max(365) int dayBasis,
        boolean recallAllowed,@Min(1) int marginHours,@Min(1) int recallHours,@NotEmpty List<@Valid RuleRequest> collateralRules,
    @NotNull @Pattern(regexp = "DAILY|WEEKLY|MONTHLY|AT_CLOSE") String rebateSettlementFrequency) {

  public AgreementRequest {
    if (rebateSettlementFrequency == null || rebateSettlementFrequency.isBlank()) {
      rebateSettlementFrequency = "AT_CLOSE";
    }
  }

  public AgreementRequest(long lenderId, String number, LocalDate effectiveDate, LocalDate expiryDate,
      BigDecimal creditLimit, BigDecimal feeRate, BigDecimal rebateRate, BigDecimal coverageRatio,
      String billingFrequency, int dayBasis, boolean recallAllowed, int marginHours, int recallHours,
      List<RuleRequest> collateralRules) {
    this(lenderId, number, effectiveDate, expiryDate, creditLimit, feeRate, rebateRate, coverageRatio,
        billingFrequency, dayBasis, recallAllowed, marginHours, recallHours, collateralRules, "AT_CLOSE");
  }
}
