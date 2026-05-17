package com.lynx.apigateway.service.impl;

import com.lynx.apigateway.dto.response.HoldingDto;
import com.lynx.apigateway.dto.response.PortfolioResponse;
import com.lynx.apigateway.dto.response.PortfolioTimeseriesPointDto;
import com.lynx.apigateway.dto.response.PortfolioTimeseriesResponse;
import com.lynx.apigateway.dto.response.StockDetailsResponse;
import com.lynx.apigateway.dto.wallet.CashBalanceDto;
import com.lynx.apigateway.dto.wallet.WalletBalanceResponse;
import com.lynx.apigateway.dto.wallet.WalletDto;
import com.lynx.apigateway.dto.wallet.WalletTransactionDto;
import com.lynx.apigateway.service.MarketFacade;
import com.lynx.apigateway.service.PortfolioFacade;
import com.lynx.apigateway.service.WalletFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.NavigableMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Service
public class PortfolioServiceFacade implements PortfolioFacade {

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceFacade.class);

    private final RestClient restClient;
    private final String portfolioServiceUrl;
    private final String walletServiceUrl;
    private final WalletFacade walletFacade;
    private final MarketFacade marketFacade;

    public PortfolioServiceFacade(
            RestClient.Builder restClientBuilder,
            @Value("${services.portfolio.url}") String portfolioServiceUrl,
            @Value("${services.wallet.url}") String walletServiceUrl,
            WalletFacade walletFacade,
            MarketFacade marketFacade
    ){
        this.restClient = restClientBuilder.build();
        this.portfolioServiceUrl = portfolioServiceUrl;
        this.walletServiceUrl = walletServiceUrl;
        this.walletFacade = walletFacade;
        this.marketFacade = marketFacade;
    }

    @Override
    public PortfolioResponse getPortfolio(UUID userId) {
        List<HoldingDto> holdings = restClient.get()
                .uri(portfolioServiceUrl + "/portfolio")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (holdings == null) {
            holdings = List.of();
        }

        for (HoldingDto holding : holdings)
            log.debug("Holding fetched: ticker={}, quantity={}, averageCost={}", holding.ticker(), holding.quantity(), holding.averageCost());

        WalletBalanceResponse balanceResponse = walletFacade.getBalance(userId, "USD");
        WalletDto wallet = balanceResponse.wallet();
        
        CashBalanceDto cashBalance = new CashBalanceDto(
                wallet.currency(),
                wallet.availableBalance(),
                wallet.reservedBalance()
        );

        return new PortfolioResponse(List.of(cashBalance), holdings);
    }

    @Override
    public PortfolioTimeseriesResponse getPortfolioTimeseries(UUID userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        List<PositionEventDto> trades = fetchUserPositionEvents(userId);
        List<WalletTransactionDto> transactions = fetchWalletTransactions(userId, "USD");
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceHistoryByTicker = loadPriceHistory(trades, endDate);

        Map<LocalDate, BigDecimal> cashDeltasByDate = new HashMap<>();
        for (WalletTransactionDto transaction : transactions) {
            BigDecimal delta = cashDelta(transaction);
            if (delta.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            cashDeltasByDate.merge(transaction.createdAt().toLocalDate(), delta, BigDecimal::add);
        }

        Map<LocalDate, Map<String, BigDecimal>> quantityDeltasByDate = new HashMap<>();
        for (PositionEventDto trade : trades) {
            BigDecimal signedQuantity = "SELL".equalsIgnoreCase(trade.side())
                    ? trade.quantity().negate()
                    : trade.quantity();

            quantityDeltasByDate
                    .computeIfAbsent(trade.executedAt().toLocalDate(), ignored -> new HashMap<>())
                    .merge(trade.instrumentId(), signedQuantity, BigDecimal::add);
        }

        BigDecimal cashValue = BigDecimal.ZERO;
        Map<String, BigDecimal> holdings = new HashMap<>();

        for (Map.Entry<LocalDate, BigDecimal> entry : cashDeltasByDate.entrySet()) {
            if (entry.getKey().isBefore(startDate)) {
                cashValue = cashValue.add(entry.getValue());
            }
        }

        for (Map.Entry<LocalDate, Map<String, BigDecimal>> entry : quantityDeltasByDate.entrySet()) {
            if (!entry.getKey().isBefore(startDate)) {
                continue;
            }
            for (Map.Entry<String, BigDecimal> tickerDelta : entry.getValue().entrySet()) {
                applyHoldingDelta(holdings, tickerDelta.getKey(), tickerDelta.getValue());
            }
        }

        List<PortfolioTimeseriesPointDto> points = new ArrayList<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            cashValue = cashValue.add(cashDeltasByDate.getOrDefault(date, BigDecimal.ZERO));

            for (Map.Entry<String, BigDecimal> tickerDelta : quantityDeltasByDate.getOrDefault(date, Map.of()).entrySet()) {
                applyHoldingDelta(holdings, tickerDelta.getKey(), tickerDelta.getValue());
            }

            BigDecimal stocksValue = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> holding : holdings.entrySet()) {
                if (holding.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal price = resolvePrice(priceHistoryByTicker.get(holding.getKey()), date);
                stocksValue = stocksValue.add(holding.getValue().multiply(price));
            }

            points.add(new PortfolioTimeseriesPointDto(
                    date.toString(),
                    cashValue,
                    stocksValue,
                    cashValue.add(stocksValue)
            ));
        }

        return new PortfolioTimeseriesResponse(points);
    }

    private void applyHoldingDelta(Map<String, BigDecimal> holdings, String ticker, BigDecimal delta) {
        BigDecimal updated = holdings.getOrDefault(ticker, BigDecimal.ZERO).add(delta);
        if (updated.compareTo(BigDecimal.ZERO) <= 0) {
            holdings.remove(ticker);
            return;
        }
        holdings.put(ticker, updated);
    }

    private BigDecimal cashDelta(WalletTransactionDto transaction) {
        return switch (transaction.transactionType()) {
            case "DEPOSIT" -> transaction.amount();
            case "ORDER_CAPTURE" -> transaction.amount().negate();
            default -> BigDecimal.ZERO;
        };
    }

    private List<PositionEventDto> fetchUserPositionEvents(UUID userId) {
        List<PositionEventDto> events = restClient.get()
                .uri(portfolioServiceUrl + "/portfolio/events?instrumentType=STOCK")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (events == null) {
            return List.of();
        }

        return events.stream()
                .filter(e -> e.executedAt() != null)
                .sorted(Comparator.comparing(PositionEventDto::executedAt))
                .toList();
    }

    private List<WalletTransactionDto> fetchWalletTransactions(UUID userId, String currency) {
        List<WalletTransactionDto> transactions = restClient.get()
                .uri(walletServiceUrl + "/funds/transactions/all?currency={currency}", currency)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (transactions == null) {
            return List.of();
        }

        return transactions.stream()
                .filter(transaction -> transaction.createdAt() != null)
                .sorted(Comparator.comparing(WalletTransactionDto::createdAt))
                .toList();
    }

    private Map<String, NavigableMap<LocalDate, BigDecimal>> loadPriceHistory(List<PositionEventDto> trades, LocalDate today) {
        Set<String> tickers = new HashSet<>();
        for (PositionEventDto trade : trades) {
            tickers.add(trade.instrumentId());
        }

        Map<String, NavigableMap<LocalDate, BigDecimal>> historyByTicker = new LinkedHashMap<>();

        for (String ticker : tickers) {
            try {
                StockDetailsResponse stockDetails = marketFacade.getStockByTicker(ticker);
                NavigableMap<LocalDate, BigDecimal> prices = new TreeMap<>();

                if (stockDetails.chartData() != null) {
                    stockDetails.chartData().forEach(candle -> {
                        LocalDate date = parseDate(candle.timestamp());
                        if (date != null && candle.close() != null) {
                            prices.put(date, candle.close());
                        }
                    });
                }

                if (stockDetails.stock() != null && stockDetails.stock().currentPrice() != null) {
                    prices.put(today, stockDetails.stock().currentPrice());
                }

                historyByTicker.put(ticker, prices);
            } catch (Exception ex) {
                log.warn("Failed to load price history for {}: {}", ticker, ex.getMessage());
                historyByTicker.put(ticker, new TreeMap<>());
            }
        }

        return historyByTicker;
    }

    private BigDecimal resolvePrice(NavigableMap<LocalDate, BigDecimal> history, LocalDate date) {
        if (history == null || history.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map.Entry<LocalDate, BigDecimal> floor = history.floorEntry(date);
        if (floor != null) {
            return floor.getValue();
        }

        return history.firstEntry().getValue();
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

    private record PositionEventDto(
            UUID id,
            UUID userId,
            String instrumentId,
            String instrumentType,
            String side,
            BigDecimal quantity,
            BigDecimal price,
            LocalDateTime executedAt
    ) {}
}
