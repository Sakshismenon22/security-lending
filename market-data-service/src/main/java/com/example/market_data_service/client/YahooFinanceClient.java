package com.example.market_data_service.client;

import com.example.market_data_service.dto.YahooChartResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

    private final RestClient yahooRestClient;

    @CircuitBreaker(name = "yahooFinance", fallbackMethod = "fetchChartDataFallback")
    public YahooChartResponse fetchChartData(
            String yahooSymbol,
            Instant period1,
            Instant period2,
            String interval
    ) {
        long period1Seconds = period1.getEpochSecond();
        long period2Seconds = period2.getEpochSecond();

        return yahooRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("period1", period1Seconds)
                        .queryParam("period2", period2Seconds)
                        .queryParam("interval", interval)
                        .queryParam("includePrePost", false)
                        .queryParam("events", "div|split|earn")
                        .queryParam("usePreMarketChartPreviousClose", true)
                        .queryParam("lang", "en-US")
                        .queryParam("region", "US")
                        .queryParam("source", "cosaic")
                        .build(yahooSymbol))
                .retrieve()
                .body(YahooChartResponse.class);
    }
    private YahooChartResponse fetchChartDataFallback(
            String yahooSymbol,
            Instant period1,
            Instant period2,
            String interval,
            Throwable cause
    ) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Yahoo Finance is temporarily unavailable; market data could not be retrieved",
                cause
        );
    }

}
