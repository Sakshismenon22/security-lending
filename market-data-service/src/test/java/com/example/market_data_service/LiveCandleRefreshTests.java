package com.example.market_data_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.market_data_service.client.YahooFinanceClient;
import com.example.market_data_service.dto.MarketCandle;
import com.example.market_data_service.dto.YahooChartResponse;
import com.example.market_data_service.mapper.YahooCandleMapper;
import com.example.market_data_service.model.SecuritiesCurrentPrice;
import com.example.market_data_service.model.SecurityMaster;
import com.example.market_data_service.repository.SecuritiesCurrentPriceRepository;
import com.example.market_data_service.repository.SecurityDataRepository;
import com.example.market_data_service.repository.SecurityMasterRepository;
import com.example.market_data_service.service.MarketDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class LiveCandleRefreshTests {
    @Test
    void refreshCatchesUpFromLastCompletedCandleAndPublishesOnlyAfterCommit() {
        var securities = mock(SecurityMasterRepository.class);
        var history = mock(SecurityDataRepository.class);
        var prices = mock(SecuritiesCurrentPriceRepository.class);
        var yahoo = mock(YahooFinanceClient.class);
        var mapper = mock(YahooCandleMapper.class);
        var messaging = mock(SimpMessagingTemplate.class);
        var service = new MarketDataService(securities, history, prices, yahoo, mapper, messaging,
                mock(TransactionTemplate.class), mock(ApplicationEventPublisher.class));

        var security = new SecurityMaster();
        security.setId(42);
        security.setSymbol("TEST");
        security.setExchange("NSE");
        var now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        var last = now.minus(3, ChronoUnit.MINUTES);
        var current = new SecuritiesCurrentPrice();
        current.setTradeDateTimestamp(last);
        var response = new YahooChartResponse();

        when(prices.findBySecurityId(42)).thenReturn(Optional.of(current));
        when(securities.lockById(42)).thenReturn(Optional.of(security));
        when(yahoo.fetchChartData(anyString(), any(), any(), eq("1m"))).thenReturn(response);
        when(mapper.map(response)).thenReturn(List.of(
                candle(last), candle(last.plusSeconds(60)), candle(last.plusSeconds(120)), candle(now.plusSeconds(60))));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.processSecurity(security);
            verify(yahoo).fetchChartData(anyString(), eq(last.minus(5, ChronoUnit.MINUTES)), any(), eq("1m"));
            verifyNoInteractions(messaging);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        var messages = ArgumentCaptor.forClass(MarketDataService.CurrentPriceMessage.class);
        verify(messaging, times(3)).convertAndSend(eq("/topic/price/TEST"), messages.capture());
        assertEquals(List.of(last, last.plusSeconds(60), last.plusSeconds(120)),
                messages.getAllValues().stream().map(MarketDataService.CurrentPriceMessage::tradeDateTimestamp).toList());
    }

    private static MarketCandle candle(Instant time) {
        return new MarketCandle(time, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L);
    }
}
