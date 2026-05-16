package com.lynx.apigateway.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record MarketStatusResponse(
        @JsonProperty("connection_status")
        String connectionStatus,

        @JsonProperty("exchange_connected")
        boolean exchangeConnected,

        @JsonProperty("market_status")
        String marketStatus,

        @JsonProperty("is_open")
        boolean isOpen,

        @JsonProperty("platform_id")
        String platformId,

        @JsonProperty("market_time")
        String marketTime,

        @JsonProperty("market_date")
        String marketDate,

        @JsonProperty("real_time")
        String realTime,

        @JsonProperty("speed_multiplier")
        BigDecimal speedMultiplier,

        @JsonProperty("last_sync_market_time")
        String lastSyncMarketTime,

        @JsonProperty("last_sync_at")
        String lastSyncAt
) {
}
