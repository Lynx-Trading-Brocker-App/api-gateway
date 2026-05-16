package com.lynx.apigateway.service.impl;

import com.lynx.apigateway.dto.order.CandleDto;
import com.lynx.apigateway.dto.response.MarketEventsResponse;
import com.lynx.apigateway.dto.response.MarketStatusResponse;
import com.lynx.apigateway.dto.response.OptionsListResponse;
import com.lynx.apigateway.dto.response.StockDetailsResponse;
import com.lynx.apigateway.dto.response.StockHistoryResponse;
import com.lynx.apigateway.dto.response.StockListResponse;
import com.lynx.apigateway.error.NotFoundException;
import com.lynx.apigateway.error.ValidationException;
import com.lynx.apigateway.service.MarketFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
@Primary
@Slf4j
public class ExchangeMarketFacade implements MarketFacade {

    private final RestClient restClient;

    @Value("${services.notification.url}")
    private String notificationUrl;

    public ExchangeMarketFacade(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public StockListResponse getStocks(String sector, Integer page, Integer limit) {
        try {
            StockListResponse response = restClient.get()
                    .uri(notificationUrl + "/internal/market/stocks")
                    .retrieve()
                    .body(StockListResponse.class);
            return response != null ? response : new StockListResponse(List.of());
        } catch (Exception e) {
            log.error("Failed to fetch stocks from notification service: {}", e.getMessage());
            return new StockListResponse(List.of());
        }
    }

    @Override
    public StockDetailsResponse getStockByTicker(String ticker) {
        try {
            StockDetailsResponse response = restClient.get()
                    .uri(notificationUrl + "/internal/market/stocks/" + ticker)
                    .retrieve()
                    .body(StockDetailsResponse.class);
            if (response == null) throw new NotFoundException("Stock ticker " + ticker + " not found.");
            return response;
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch stock {} from notification service: {}", ticker, e.getMessage());
            throw new NotFoundException("Stock ticker " + ticker + " not found.");
        }
    }

    @Override
    public StockHistoryResponse getStockHistory(String ticker, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ValidationException("'from' must be on or before 'to'.");
        }

        StockDetailsResponse stockDetails = getStockByTicker(ticker);
        List<CandleDto> chartData = stockDetails.chartData() == null
                ? List.of()
                : stockDetails.chartData().stream()
                .filter(candle -> isWithinRange(candle, from, to))
                .toList();

        String resolvedTicker = stockDetails.stock() != null
                ? stockDetails.stock().ticker()
                : ticker.toUpperCase(Locale.ROOT);

        return new StockHistoryResponse(
                resolvedTicker,
                from.toString(),
                to.toString(),
                chartData
        );
    }

    @Override
    public MarketStatusResponse getMarketStatus() {
        try {
            MarketStatusResponse response = restClient.get()
                    .uri(notificationUrl + "/internal/market/status")
                    .retrieve()
                    .body(MarketStatusResponse.class);
            return response != null
                    ? response
                    : new MarketStatusResponse("DISCONNECTED", false, "CLOSED", false, null, null, null, null, null, null, null);
        } catch (Exception e) {
            log.error("Failed to fetch market status from notification service: {}", e.getMessage());
            return new MarketStatusResponse("DISCONNECTED", false, "CLOSED", false, null, null, null, null, null, null, null);
        }
    }

    @Override
    public OptionsListResponse getOptions() {
        try {
            OptionsListResponse response = restClient.get()
                    .uri(notificationUrl + "/internal/market/options")
                    .retrieve()
                    .body(OptionsListResponse.class);
            return response != null ? response : new OptionsListResponse(List.of());
        } catch (Exception e) {
            log.error("Failed to fetch options from notification service: {}", e.getMessage());
            return new OptionsListResponse(List.of());
        }
    }

    @Override
    public MarketEventsResponse getMarketEvents() {
        try {
            MarketEventsResponse response = restClient.get()
                    .uri(notificationUrl + "/internal/market/events")
                    .retrieve()
                    .body(MarketEventsResponse.class);
            return response != null ? response : new MarketEventsResponse(List.of());
        } catch (Exception e) {
            log.error("Failed to fetch market events from notification service: {}", e.getMessage());
            return new MarketEventsResponse(List.of());
        }
    }

    private boolean isWithinRange(CandleDto candle, LocalDate from, LocalDate to) {
        LocalDate candleDate = parseDate(candle.timestamp());
        return candleDate != null && !candleDate.isBefore(from) && !candleDate.isAfter(to);
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return Instant.parse(raw).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(raw).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ignored) {
            log.debug("Unable to parse market timestamp: {}", raw);
            return null;
        }
    }
}
