package com.example.market_data_service.controller;

import com.example.market_data_service.dto.SecurityCandleResponse;
import com.example.market_data_service.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/security-data")
@RequiredArgsConstructor
public class SecurityDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/history")
    public ResponseEntity<List<SecurityCandleResponse>>
    getHistoricalCandles(
            @RequestParam String symbol,
            @RequestParam String exchange
    ) {
        List<SecurityCandleResponse> candles =
                marketDataService.getAllHistoricalCandles(
                        symbol,
                        exchange
                );
        log.info("Candle",candles);
        return ResponseEntity.ok(candles);
    }
}
