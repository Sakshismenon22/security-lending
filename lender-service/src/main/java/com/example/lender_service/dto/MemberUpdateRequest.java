package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record MemberUpdateRequest(
    @Pattern(regexp = "ADMIN|LENDING_DESK|RISK|TREASURY|OPERATIONS|READ_ONLY") @NotNull String role,
    boolean active) {}
