package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record MemberRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @Pattern(regexp = "ADMIN|LENDING_DESK|RISK|TREASURY|OPERATIONS|READ_ONLY") @NotNull
        String role) {}
