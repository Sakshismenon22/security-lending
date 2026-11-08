package com.example.market_data_service.dto;

import java.math.BigDecimal;
import java.time.Instant;


public record SecurityCandleResponse(
        Long id,
        Integer securityId,
        String symbol,
        String exchange,
        Instant tradedDateTimestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {
}