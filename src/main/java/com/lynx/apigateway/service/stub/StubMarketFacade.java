package com.lynx.apigateway.service.stub;

import com.lynx.apigateway.dto.order.CandleDto;
import com.lynx.apigateway.dto.response.MarketEventsResponse;
import com.lynx.apigateway.dto.response.MarketStatusResponse;
import com.lynx.apigateway.dto.response.OptionsListResponse;
import com.lynx.apigateway.dto.response.StockDetailsResponse;
import com.lynx.apigateway.dto.response.StockHistoryResponse;
import com.lynx.apigateway.dto.response.StockDto;
import com.lynx.apigateway.dto.response.StockListResponse;
import com.lynx.apigateway.error.NotFoundException;
import com.lynx.apigateway.error.ValidationException;
import com.lynx.apigateway.service.MarketFacade;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StubMarketFacade implements MarketFacade {

    private static final Set<String> VALID_SECTORS = Set.of("TECH", "FINANCE", "ENERGY");

    @Override
    public StockListResponse getStocks(String sector, Integer page, Integer limit) {
        int safePage = page == null ? 0 : page;
        int safeLimit = limit == null ? 10 : limit;

        if (safePage < 0) {
            throw new ValidationException("Page number must be strictly greater than 0.");
        }
        if (safeLimit < 5 || safeLimit > 50) {
            throw new ValidationException("Limit must be between 1 and 100.");
        }
        if (sector != null && !VALID_SECTORS.contains(sector.toUpperCase(Locale.ROOT))) {
            throw new ValidationException("The specified sector does not exist.");
        }

        List<StockDto> stocks = List.of(
                new StockDto(
                        "ARKA",
                        "Arkadia Technologies",
                        "Tech",
                        new BigDecimal("142.50"),
                        new BigDecimal("140.00"),
                        new BigDecimal("145.20"),
                        new BigDecimal("139.50"),
                        1_250_400L,
                        "2026-04-10T09:00:00"
                ),
                new StockDto(
                        "NXGY",
                        "NextGen Energy",
                        "Energy",
                        new BigDecimal("45.20"),
                        new BigDecimal("46.10"),
                        new BigDecimal("46.50"),
                        new BigDecimal("44.80"),
                        890_000L,
                        "2026-04-12T09:00:00"
                )
        );

        return new StockListResponse(stocks);
    }

    @Override
    public StockDetailsResponse getStockByTicker(String ticker) {
        if ("ARKA".equalsIgnoreCase(ticker)) {
            StockDto stock = new StockDto(
                    "ARKA",
                    "Arkadia Technologies",
                    "Tech",
                    new BigDecimal("142.50"),
                    new BigDecimal("140.00"),
                    new BigDecimal("145.20"),
                    new BigDecimal("139.50"),
                    1_250_400L,
                    "2026-04-10T09:00:00"
            );

            List<CandleDto> chartData = List.of(
                    new CandleDto(
                            "2026-04-21T09:30:00",
                            new BigDecimal("140.00"),
                            new BigDecimal("141.50"),
                            new BigDecimal("139.50"),
                            new BigDecimal("141.00"),
                            150_000L
                    ),
                    new CandleDto(
                            "2026-04-21T10:00:00",
                            new BigDecimal("141.00"),
                            new BigDecimal("142.20"),
                            new BigDecimal("140.80"),
                            new BigDecimal("142.00"),
                            220_000L
                    ),
                    new CandleDto(
                            "2026-04-21T10:30:00",
                            new BigDecimal("142.00"),
                            new BigDecimal("145.20"),
                            new BigDecimal("141.50"),
                            new BigDecimal("142.50"),
                            310_400L
                    )
            );
            return new StockDetailsResponse(stock, chartData);
        } else if ("NXGY".equalsIgnoreCase(ticker) || "NGXY".equalsIgnoreCase(ticker)) {
            StockDto stock = new StockDto(
                    "NXGY",
                    "NextGen Energy",
                    "Energy",
                    new BigDecimal("45.20"),
                    new BigDecimal("46.10"),
                    new BigDecimal("46.50"),
                    new BigDecimal("44.80"),
                    890_000L,
                    "2026-04-12T09:00:00"
            );

            List<CandleDto> chartData = List.of(
                    new CandleDto(
                            "2026-04-21T09:30:00",
                            new BigDecimal("44.80"),
                            new BigDecimal("45.50"),
                            new BigDecimal("44.50"),
                            new BigDecimal("45.00"),
                            100_000L
                    ),
                    new CandleDto(
                            "2026-04-21T10:00:00",
                            new BigDecimal("45.00"),
                            new BigDecimal("46.10"),
                            new BigDecimal("44.80"),
                            new BigDecimal("45.20"),
                            150_000L
                    )
            );
            return new StockDetailsResponse(stock, chartData);
        } else {
            throw new NotFoundException("Stock ticker " + ticker + " not found.");
        }
    }

    @Override
    public StockHistoryResponse getStockHistory(String ticker, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ValidationException("'from' must be on or before 'to'.");
        }

        StockDetailsResponse stockDetails = getStockByTicker(ticker);
        List<CandleDto> chartData = stockDetails.chartData().stream()
                .filter(candle -> isWithinRange(candle, from, to))
                .toList();

        return new StockHistoryResponse(
                stockDetails.stock().ticker(),
                from.toString(),
                to.toString(),
                chartData
        );
    }

    @Override
    public MarketStatusResponse getMarketStatus() {
        Instant now = Instant.now();
        return new MarketStatusResponse(
                "CONNECTED",
                true,
                "OPEN",
                true,
                "stub-platform",
                now.toString(),
                now.atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString(),
                now.toString(),
                new BigDecimal("1.0000"),
                now.toString(),
                now.toString()
        );
    }

    @Override
    public OptionsListResponse getOptions() {
        return new OptionsListResponse(List.of());
    }

    @Override
    public MarketEventsResponse getMarketEvents() {
        return new MarketEventsResponse(List.of());
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
            return null;
        }
    }
}
