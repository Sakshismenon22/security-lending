package com.example.market_data_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketCandle(
        Instant tradedDateTimestamp,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        Long volume
) {
}
