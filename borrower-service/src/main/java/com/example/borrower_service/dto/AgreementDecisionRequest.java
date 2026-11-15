package com.example.borrower_service.dto;

import jakarta.validation.constraints.Size;

/** The borrower can only accept or decline lender-authored MSLA terms. */
public record AgreementDecisionRequest(@Size(max = 500) String reason) {}
