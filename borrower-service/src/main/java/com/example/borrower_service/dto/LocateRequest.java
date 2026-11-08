package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record LocateRequest(@Positive long agreementId,@Positive long securityId,@Positive long quantity,@NotNull @Future Instant expiresAt) {}
