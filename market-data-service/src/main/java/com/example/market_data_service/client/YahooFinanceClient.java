package com.example.market_data_service.client;

import com.example.market_data_service.dto.YahooChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class YahooFinanceClient {

    private final RestClient yahooRestClient;

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
                        .queryParam(
                                "events",
                                "div|split|earn"
                        )
                        .queryParam(
                                "usePreMarketChartPreviousClose",
                                true
                        )
                        .queryParam("lang", "en-US")
                        .queryParam("region", "US")
                        .queryParam("source", "cosaic")
                        .build(yahooSymbol))
                .retrieve()
                .body(YahooChartResponse.class);
    }
}
