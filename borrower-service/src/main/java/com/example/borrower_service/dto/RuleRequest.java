package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record RuleRequest(@NotNull @Pattern(regexp="CASH|SECURITY") String assetType,Long securityId,
                       @NotNull @DecimalMin("0") @DecimalMax("0.99999999") BigDecimal haircut) {}
