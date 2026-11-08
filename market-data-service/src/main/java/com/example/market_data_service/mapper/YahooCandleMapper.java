package com.example.market_data_service.mapper;

import com.example.market_data_service.dto.MarketCandle;
import com.example.market_data_service.dto.YahooChartResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class YahooCandleMapper {

    private static final long ONE_MINUTE_SECONDS = 60L;

    public List<MarketCandle> map(
            YahooChartResponse response
    ) {
        YahooChartResponse.Result result =
                extractFirstResult(response);

        if (result == null) {
            return List.of();
        }

        YahooChartResponse.Quote quote =
                extractFirstQuote(result);

        if (quote == null) {
            return List.of();
        }

        List<Long> timestamps =
                result.getTimestamp();

        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }

        List<MarketCandle> candles =
                new ArrayList<>();

        for (int index = 0;
             index < timestamps.size();
             index++) {

            if (isMissingCandle(quote, index)) {
                log.debug(
                        "Skipping candle at index {} because OHLC data is missing",
                        index
                );

                continue;
            }

            Long yahooTimestamp =
                    timestamps.get(index);

            if (yahooTimestamp == null) {
                log.debug(
                        "Skipping candle at index {} because timestamp is null",
                        index
                );

                continue;
            }

            /*
             * Yahoo returns Unix epoch seconds.
             *
             * Normalize the value to an exact minute boundary.
             * For example:
             *
             * 09:30:05 becomes 09:30:00
             * 09:30:59 becomes 09:30:00
             */
            long minuteTimestamp =
                    Math.floorDiv(
                            yahooTimestamp,
                            ONE_MINUTE_SECONDS
                    ) * ONE_MINUTE_SECONDS;

            Instant candleTimestamp =
                    Instant.ofEpochSecond(
                            minuteTimestamp
                    ).truncatedTo(
                            ChronoUnit.MINUTES
                    );

            BigDecimal open =
                    quote.getOpen().get(index);

            BigDecimal close =
                    quote.getClose().get(index);

            BigDecimal reportedHigh =
                    quote.getHigh().get(index);

            BigDecimal reportedLow =
                    quote.getLow().get(index);

            /*
             * Ensure the high is not below the open or close.
             */
            BigDecimal high =
                    max(
                            reportedHigh,
                            open,
                            close
                    );

            /*
             * Ensure the low is not above the open or close.
             */
            BigDecimal low =
                    min(
                            reportedLow,
                            open,
                            close
                    );

            MarketCandle candle =
                    new MarketCandle(
                            candleTimestamp,
                            open,
                            close,
                            high,
                            low,
                            getVolume(quote, index)
                    );

            candles.add(candle);
        }

        List<MarketCandle> normalizedCandles =
                sortAndRemoveDuplicates(candles);

        validateIntervals(normalizedCandles);

        return normalizedCandles;
    }

    private YahooChartResponse.Result extractFirstResult(
            YahooChartResponse response
    ) {
        if (response == null
                || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {

            log.debug(
                    "Yahoo response does not contain a chart result"
            );

            return null;
        }

        return response.getChart()
                .getResult()
                .get(0);
    }

    private YahooChartResponse.Quote extractFirstQuote(
            YahooChartResponse.Result result
    ) {
        if (result.getIndicators() == null
                || result.getIndicators().getQuote() == null
                || result.getIndicators().getQuote().isEmpty()) {

            log.debug(
                    "Yahoo response does not contain quote data"
            );

            return null;
        }

        return result.getIndicators()
                .getQuote()
                .get(0);
    }

    private List<MarketCandle> sortAndRemoveDuplicates(
            List<MarketCandle> candles
    ) {
        candles.sort(
                Comparator.comparing(
                        MarketCandle::tradedDateTimestamp
                )
        );

        Map<Instant, MarketCandle> candlesByTimestamp =
                new LinkedHashMap<>();

        for (MarketCandle candle : candles) {
            /*
             * If Yahoo returns the same minute more than once,
             * retain the final version of that minute.
             */
            candlesByTimestamp.put(
                    candle.tradedDateTimestamp(),
                    candle
            );
        }

        return new ArrayList<>(
                candlesByTimestamp.values()
        );
    }

    private void validateIntervals(
            List<MarketCandle> candles
    ) {
        for (int index = 1;
             index < candles.size();
             index++) {

            Instant previousTimestamp =
                    candles.get(index - 1)
                            .tradedDateTimestamp();

            Instant currentTimestamp =
                    candles.get(index)
                            .tradedDateTimestamp();

            long differenceSeconds =
                    currentTimestamp.getEpochSecond()
                            - previousTimestamp.getEpochSecond();

            /*
             * Gaps larger than one minute are valid when:
             *
             * - No trading occurred.
             * - The exchange was closed.
             * - The response crosses trading days.
             *
             * A difference below 60 seconds is not valid for
             * a one-minute candle series.
             */
            if (differenceSeconds < ONE_MINUTE_SECONDS) {
                log.warn(
                        "Invalid sub-minute candle interval: previous={}, current={}, difference={} seconds",
                        previousTimestamp,
                        currentTimestamp,
                        differenceSeconds
                );
            } else if (
                    differenceSeconds
                            % ONE_MINUTE_SECONDS != 0
            ) {
                log.warn(
                        "Candle interval is not aligned to one minute: previous={}, current={}, difference={} seconds",
                        previousTimestamp,
                        currentTimestamp,
                        differenceSeconds
                );
            }
        }
    }

    private boolean isMissingCandle(
            YahooChartResponse.Quote quote,
            int index
    ) {
        return quote.getOpen() == null
                || quote.getClose() == null
                || quote.getHigh() == null
                || quote.getLow() == null
                || index >= quote.getOpen().size()
                || index >= quote.getClose().size()
                || index >= quote.getHigh().size()
                || index >= quote.getLow().size()
                || quote.getOpen().get(index) == null
                || quote.getClose().get(index) == null
                || quote.getHigh().get(index) == null
                || quote.getLow().get(index) == null;
    }

    private Long getVolume(
            YahooChartResponse.Quote quote,
            int index
    ) {
        if (quote.getVolume() == null
                || index >= quote.getVolume().size()) {
            return null;
        }

        return quote.getVolume().get(index);
    }

    private BigDecimal max(
            BigDecimal first,
            BigDecimal second,
            BigDecimal third
    ) {
        return first
                .max(second)
                .max(third);
    }

    private BigDecimal min(
            BigDecimal first,
            BigDecimal second,
            BigDecimal third
    ) {
        return first
                .min(second)
                .min(third);
    }
}