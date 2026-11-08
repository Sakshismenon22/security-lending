package com.example.market_data_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooChartResponse {

    private Chart chart;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {
        private List<Result> result;
        private Object error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Meta meta;
        private List<Long> timestamp;
        private Map<String, Dividend> events;
        private Indicators indicators;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private String currency;
        private String symbol;
        private String exchangeName;
        private String fullExchangeName;
        private String instrumentType;
        private Long firstTradeDate;
        private Long regularMarketTime;
        private Boolean hasPrePostMarketData;
        private Integer gmtoffset;
        private String timezone;
        private String exchangeTimezoneName;
        private BigDecimal regularMarketPrice;
        private BigDecimal regularMarketChangePercent;
        private BigDecimal fulldayPrice;
        private BigDecimal fulldayChange;
        private BigDecimal fulldayChangePercent;
        private BigDecimal fiftyTwoWeekHigh;
        private BigDecimal fiftyTwoWeekLow;
        private BigDecimal regularMarketDayHigh;
        private BigDecimal regularMarketDayLow;
        private Long regularMarketVolume;
        private String longName;
        private String shortName;
        private BigDecimal chartPreviousClose;
        private BigDecimal previousClose;
        private Integer scale;
        private Integer priceHint;
        private TradingPeriods currentTradingPeriod;
        private List<List<TradingPeriod>> tradingPeriods;
        private String dataGranularity;
        private String range;
        private List<String> validRanges;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradingPeriods {
        private TradingPeriod pre;
        private TradingPeriod regular;
        private TradingPeriod post;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradingPeriod {
        private String timezone;
        private Long start;
        private Long end;
        private Integer gmtoffset;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Indicators {
        private List<Quote> quote;
        private List<Quote> adjclose;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        private List<BigDecimal> open;
        private List<BigDecimal> close;
        private List<BigDecimal> high;
        private List<BigDecimal> low;
        private List<Long> volume;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dividend {
        private BigDecimal amount;
        private Long date;
    }
}
