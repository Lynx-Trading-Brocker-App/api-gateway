package com.lynx.apigateway.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceOrderRequest(
        @NotBlank(message = "Instrument type cannot be empty.")
        @JsonProperty("instrument_type")
        String instrumentType,

        @NotBlank(message = "Instrument id cannot be empty.")
        @JsonProperty("instrument_id")
        String instrumentId,

        @NotBlank(message = "Order type cannot be empty.")
        @JsonProperty("order_type")
        String orderType,

        @NotBlank(message = "Side cannot be empty.")
        @JsonProperty("side")
        String side,

        @NotNull(message = "Quantity cannot be null.")
        @Min(value = 1, message = "Quantity must be at least 1.")
        @JsonProperty("quantity")
        Integer quantity,

        @DecimalMin(value = "0.01", message = "Limit price must be greater than 0.")
        @JsonProperty("limit_price")
        BigDecimal limitPrice
) {
}