package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record MemberUpdateRequest(@NotBlank @Pattern(regexp="ADMIN|TRADER|TREASURY|OPERATIONS|READ_ONLY") String role,boolean active) {}
