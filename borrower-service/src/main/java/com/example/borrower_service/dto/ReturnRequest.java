package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record ReturnRequest(@Positive long quantity,@Positive Long recallId) {
    public ReturnRequest(long quantity) {this(quantity,null);}
}
