package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record MemberRequest(@NotBlank @Size(max=160) String name,@Email @NotBlank @Size(max=254) String email,
                         @NotBlank @Pattern(regexp="ADMIN|TRADER|TREASURY|OPERATIONS|READ_ONLY") String role) {}
