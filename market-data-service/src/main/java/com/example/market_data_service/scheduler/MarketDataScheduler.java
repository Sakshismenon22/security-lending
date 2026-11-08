package com.example.market_data_service.scheduler;

import com.example.market_data_service.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final MarketDataService marketDataService;

    private final AtomicBoolean batchRunning =
            new AtomicBoolean(false);

    /*
     * Runs at:
     *
     * 09:30:05
     * 09:31:05
     * 09:32:05
     *
     * Uses Asia/Kolkata exchange time.
     */
    @Scheduled(
            cron = "5 * * * * *",
            zone = "Asia/Kolkata"
    )
    public void runMarketDataBatch() {
        if (!batchRunning.compareAndSet(
                false,
                true
        )) {
            log.warn(
                    "Skipping batch because the previous batch is still running"
            );

            return;
        }

        try {
            marketDataService
                    .processAllSecurities();
        } catch (Exception exception) {
            log.error(
                    "Market-data batch failed",
                    exception
            );
        } finally {
            batchRunning.set(false);
        }
    }
}
