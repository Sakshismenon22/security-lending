package com.example.market_data_service.service;

import com.example.market_data_service.client.YahooFinanceClient;
import com.example.market_data_service.dto.MarketCandle;
import com.example.market_data_service.dto.SecurityCandleResponse;
import com.example.market_data_service.dto.YahooChartResponse;
import com.example.market_data_service.mapper.YahooCandleMapper;
import com.example.market_data_service.model.SecuritiesCurrentPrice;
import com.example.market_data_service.model.SecurityData;
import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.repository.SecuritiesCurrentPriceRepository;
import com.example.market_data_service.repository.SecurityDataRepository;
import com.example.market_data_service.repository.SecurityMasterRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private static final String ONE_MINUTE_INTERVAL = "1m";

    /*
     * Number of minutes requested from Yahoo on every poll.
     *
     * The overlap allows the service to receive corrections to
     * recently returned candles and prevents missing a candle
     * because of network delay.
     */
    private static final long LOOKBACK_MINUTES = 10000L;

    private final SecurityMasterRepository
            securityMasterRepository;

    private final SecurityDataRepository
            securityDataRepository;

    private final SecuritiesCurrentPriceRepository
            securityCurrentPriceRepository;

    private final YahooFinanceClient yahooFinanceClient;

    private final YahooCandleMapper yahooCandleMapper;

    private final SimpMessagingTemplate messagingTemplate;

    /*
     * Returns all historical candles for one symbol and exchange.
     */
    @Transactional
    public List<SecurityCandleResponse>
    getAllHistoricalCandles(
            String symbol,
            String exchange
    ) {
        validateSymbolAndExchange(
                symbol,
                exchange
        );

        String normalizedSymbol =
                normalizeSymbol(symbol);

        String normalizedExchange =
                normalizeExchange(exchange);

        SecurityMaster security =
                securityMasterRepository
                        .findBySymbolIgnoreCaseAndExchangeIgnoreCase(
                                normalizedSymbol,
                                normalizedExchange
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Security not found: "
                                                + normalizedSymbol
                                                + " on "
                                                + normalizedExchange
                                )
                        );

        List<SecurityData> history =
                securityDataRepository.findAllHistory(
                        security.getId()
                );

        return history.stream()
                .map(data ->
                        toCandleResponse(
                                security,
                                data
                        )
                )
                .toList();
    }

    /*
     * Processes all securities stored in security_master.
     */
    @Transactional
    public void processAllSecurities() {
        List<SecurityMaster> securities =
                securityMasterRepository.findAll();

        log.info(
                "Starting market-data batch for {} securities",
                securities.size()
        );

        for (SecurityMaster security : securities) {
            try {
                processSecurity(security);
            } catch (Exception exception) {
                log.error(
                        "Failed to process symbol={}",
                        security.getSymbol(),
                        exception
                );
            }
        }

        log.info("Market-data batch completed");
    }

    /*
     * Processes one security.
     *
     * The current minute is deliberately excluded from the
     * current-price update because it is still forming.
     */
    @Transactional
    public void processSecurity(
            SecurityMaster security
    ) {
        if (security == null) {
            log.warn(
                    "Skipping null security"
            );

            return;
        }

        Instant requestTime = Instant.now();

        /*
         * Request the previous five minutes plus a small future
         * buffer. The future buffer helps Yahoo include the
         * latest available candle near the request boundary.
         */
        Instant period1 =
                requestTime.minus(
                        LOOKBACK_MINUTES,
                        ChronoUnit.MINUTES
                );

        Instant period2 =
                requestTime.plusSeconds(5);

        String yahooSymbol =
                buildYahooSymbol(security);

        log.debug(
                "Fetching symbol={} yahooSymbol={} period1={} period2={}",
                security.getSymbol(),
                yahooSymbol,
                period1,
                period2
        );

        YahooChartResponse response =
                yahooFinanceClient.fetchChartData(
                        yahooSymbol,
                        period1,
                        period2,
                        ONE_MINUTE_INTERVAL
                );

        List<MarketCandle> candles =
                yahooCandleMapper.map(response);

        if (candles == null || candles.isEmpty()) {
            log.warn(
                    "No candles returned for symbol={}",
                    security.getSymbol()
            );

            return;
        }

        /*
         * Save every returned candle.
         *
         * This includes the currently forming candle. The
         * database upsert updates it as Yahoo provides revised
         * OHLC values.
         */
        saveHistoricalCandles(
                security,
                candles
        );

        /*
         * A candle timestamp represents the start of its minute.
         *
         * Example:
         *
         * Current time:       10:01:05
         * Current minute:     10:01:00
         * Completed candle:   10:00:00
         *
         * Therefore, select only candles before 10:01:00.
         */
        Instant currentMinuteStart =
                requestTime.truncatedTo(
                        ChronoUnit.MINUTES
                );

        MarketCandle latestCompletedCandle =
                candles.stream()
                        .filter(candle ->
                                candle != null
                                        && candle
                                        .tradedDateTimestamp()
                                        .isBefore(
                                                currentMinuteStart
                                        )
                        )
                        .max(
                                Comparator.comparing(
                                        MarketCandle
                                                ::tradedDateTimestamp
                                )
                        )
                        .orElse(null);

        if (latestCompletedCandle == null) {
            log.warn(
                    "No completed candle available for symbol={} currentMinute={}",
                    security.getSymbol(),
                    currentMinuteStart
            );

            return;
        }

        log.info(
                "Completed candle selected: symbol={} timestamp={} open={} high={} low={} close={} volume={}",
                security.getSymbol(),
                latestCompletedCandle
                        .tradedDateTimestamp(),
                latestCompletedCandle.open(),
                latestCompletedCandle.high(),
                latestCompletedCandle.low(),
                latestCompletedCandle.close(),
                latestCompletedCandle.volume()
        );

        /*
         * Update current price with the latest completed candle.
         */
        updateCurrentPrice(
                security,
                latestCompletedCandle
        );

        /*
         * Publish the completed one-minute candle.
         */
        publishCurrentPrice(
                security,
                latestCompletedCandle
        );
    }

    /*
     * Upserts all candles into security_data.
     *
     * The repository method must use a unique key such as:
     *
     * security_id + traded_date_timestamp
     */
    private void saveHistoricalCandles(
            SecurityMaster security,
            List<MarketCandle> candles
    ) {
        for (MarketCandle candle : candles) {
            if (candle == null) {
                continue;
            }

            if (candle.tradedDateTimestamp() == null) {
                log.warn(
                        "Skipping candle with null timestamp for symbol={}",
                        security.getSymbol()
                );

                continue;
            }

            securityDataRepository.upsert(
                    security.getId(),
                    candle.tradedDateTimestamp(),
                    candle.open(),
                    candle.close(),
                    candle.high(),
                    candle.low(),
                    candle.volume()
            );
        }
    }

    /*
     * Stores the latest completed candle in the current-price
     * table.
     */
    private void updateCurrentPrice(
            SecurityMaster security,
            MarketCandle candle
    ) {
        SecuritiesCurrentPrice currentPrice =
                securityCurrentPriceRepository
                        .findBySecurityId(
                                security.getId()
                        )
                        .orElseGet(
                                SecuritiesCurrentPrice::new
                        );

        currentPrice.setSecurity(security);

        /*
         * This is the candle timestamp, not the request time.
         */
        currentPrice.setTradeDateTimestamp(
                candle.tradedDateTimestamp()
        );

        currentPrice.setOpen(
                candle.open()
        );

        currentPrice.setClose(
                candle.close()
        );

        currentPrice.setHigh(
                candle.high()
        );

        currentPrice.setLow(
                candle.low()
        );

        currentPrice.setVolume(
                candle.volume()
        );

        /*
         * This is when your application updated the row.
         * It is different from the candle's trading timestamp.
         */
        currentPrice.setUpdatedAt(
                Instant.now()
        );

        securityCurrentPriceRepository.save(
                currentPrice
        );
    }

    /*
     * Publishes only the completed candle through WebSocket.
     */
    private void publishCurrentPrice(
            SecurityMaster security,
            MarketCandle candle
    ) {
        CurrentPriceMessage message =
                new CurrentPriceMessage(
                        security.getId(),
                        security.getSymbol(),
                        security.getExchange(),
                        candle.tradedDateTimestamp(),
                        candle.open(),
                        candle.close(),
                        candle.high(),
                        candle.low(),
                        candle.volume()
                );

        String destination =
                "/topic/price/"
                        + security.getSymbol();

        log.debug(
                "Publishing completed candle to {}: {}",
                destination,
                message
        );

        messagingTemplate.convertAndSend(
                destination,
                message
        );
    }

    /*
     * Builds the Yahoo Finance symbol.
     */
    private String buildYahooSymbol(
            SecurityMaster security
    ) {
        String symbol =
                security.getSymbol()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        String exchange =
                security.getExchange()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if ("NSE".equals(exchange)) {
            return symbol + ".NS";
        }

        if ("BSE".equals(exchange)) {
            return symbol + ".BO";
        }

        return symbol;
    }

    private SecurityCandleResponse
    toCandleResponse(
            SecurityMaster security,
            SecurityData data
    ) {
        return new SecurityCandleResponse(
                data.getId(),
                security.getId(),
                security.getSymbol(),
                security.getExchange(),
                data.getTradedDateTimestamp(),
                data.getOpen(),
                data.getHigh(),
                data.getLow(),
                data.getClose(),
                data.getVolume()
        );
    }

    private void validateSymbolAndExchange(
            String symbol,
            String exchange
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Symbol is required"
            );
        }

        if (exchange == null || exchange.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Exchange is required"
            );
        }
    }

    private String normalizeExchange(
            String exchange
    ) {
        return exchange
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(
            String symbol
    ) {
        String normalized =
                symbol
                        .trim()
                        .toUpperCase(Locale.ROOT);

        if (normalized.endsWith(".NS")) {
            return normalized.substring(
                    0,
                    normalized.length() - 3
            );
        }

        if (normalized.endsWith(".BO")) {
            return normalized.substring(
                    0,
                    normalized.length() - 3
            );
        }

        return normalized;
    }

    public record CurrentPriceMessage(
            Integer securityId,
            String symbol,
            String exchange,
            Instant tradeDateTimestamp,
            BigDecimal open,
            BigDecimal close,
            BigDecimal high,
            BigDecimal low,
            Long volume
    ) {
    }
}