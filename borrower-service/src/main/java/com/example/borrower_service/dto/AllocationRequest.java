package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record AllocationRequest(@NotNull @Pattern(regexp="CASH|SECURITY") String assetType,Long securityId,
        @PositiveOrZero long quantity,@NotNull @DecimalMin("0") @Digits(integer=18,fraction=6) BigDecimal cashAmount,
        @Positive Long marginCallId) {
    public AllocationRequest(String assetType,Long securityId,long quantity,BigDecimal cashAmount) {this(assetType,securityId,quantity,cashAmount,null);}
}
