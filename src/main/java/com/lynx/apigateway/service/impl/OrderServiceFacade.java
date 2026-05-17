package com.lynx.apigateway.service.impl;

import com.lynx.apigateway.dto.PaginationDto;
import com.lynx.apigateway.dto.order.*;
import com.lynx.apigateway.error.ForbiddenException;
import com.lynx.apigateway.error.NotFoundException;
import com.lynx.apigateway.error.ValidationException;
import com.lynx.apigateway.service.OrderFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderServiceFacade implements OrderFacade {

    private final RestClient restClient;
    private final String orderServiceUrl;
    private final String platformId;

    public OrderServiceFacade(
            RestClient.Builder restClientBuilder,
            @Value("${services.order.url}") String orderServiceUrl,
            @Value("${broker.platform-id}") String platformId
    ) {
        this.restClient = restClientBuilder.build();
        this.orderServiceUrl = orderServiceUrl;
        this.platformId = platformId;
    }

    @Override
    public PlaceOrderResponse placeOrder(UUID userId, PlaceOrderRequest request) {
        validatePlaceOrder(request);

        LocalDateTime now = LocalDateTime.now();

        // For MARKET orders use marketPrice as the limitPrice so the order service
        // can correctly reserve quantity × currentPrice from the wallet.
        BigDecimal priceForReservation = "MARKET".equalsIgnoreCase(request.orderType())
                ? request.marketPrice()
                : request.limitPrice();

        OrderServiceOrderRequest body = new OrderServiceOrderRequest(
                UUID.randomUUID(),
                UUID.fromString(platformId),
                userId,
                request.instrumentType(),
                request.instrumentId(),
                request.orderType(),
                request.side(),
                BigDecimal.valueOf(request.quantity()),
                priceForReservation,
                "PENDING",
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                now,
                now,
                null
        );

        OrderServiceOrderResponse savedOrder = restClient.post()
                .uri(orderServiceUrl + "/api/orders")
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String err = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new ValidationException("Order Service rejected the order: " + err);
                })
                .body(OrderServiceOrderResponse.class);

        return new PlaceOrderResponse(
                "Order accepted and routed to exchange.",
                toOrderDto(savedOrder)
        );
    }

    @Override
    public OrderHistoryResponse getOrders(UUID userId, String status, Integer page, Integer limit) {
        int safePage = page == null ? 0 : page;
        int safeLimit = limit == null ? 10 : limit;

        if (safePage < 0) {
            throw new ValidationException("Page number must be greater than or equal to 0.");
        }

        if (safeLimit < 5 || safeLimit > 50) {
            throw new ValidationException("Limit must be between 5 and 50.");
        }

        OrderServiceOrderResponse[] orders = restClient.get()
                .uri(orderServiceUrl + "/api/orders/user/" + userId)
                .retrieve()
                .body(OrderServiceOrderResponse[].class);

        List<OrderDto> filtered = Arrays.stream(orders == null ? new OrderServiceOrderResponse[0] : orders)
                .filter(order -> status == null || order.status().equalsIgnoreCase(status))
                .map(this::toOrderDto)
                .toList();

        int from = Math.min(safePage * safeLimit, filtered.size());
        int to = Math.min(from + safeLimit, filtered.size());

        List<OrderDto> pageItems = filtered.subList(from, to);

        PaginationDto pagination = new PaginationDto(
                filtered.size(),
                safePage,
                (int) Math.ceil((double) filtered.size() / safeLimit),
                safeLimit
        );

        return new OrderHistoryResponse(pageItems, pagination);
    }

    @Override
    public CancelOrderResponse cancelOrder(UUID userId, String orderId) {
        UUID parsedOrderId = UUID.fromString(orderId);

        OrderServiceOrderResponse order = restClient.get()
                .uri(orderServiceUrl + "/api/orders/" + parsedOrderId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new NotFoundException("The requested order ID does not exist.");
                })
                .body(OrderServiceOrderResponse.class);

        OrderServiceOrderResponse nonNullOrder = Objects.requireNonNull(
                order,
                "Order Service returned no order data."
        );
        if (!userId.equals(nonNullOrder.platformUserId())) {
            throw new ForbiddenException("You do not have permission to modify this resource.");
        }

        restClient.delete()
                .uri(orderServiceUrl + "/api/orders/" + parsedOrderId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ValidationException("Cannot cancel this order.");
                })
                .toBodilessEntity();

        CancelOrderDto cancelledOrder = new CancelOrderDto(
                orderId,
                "CANCELLED",
                LocalDateTime.now()
        );

        return new CancelOrderResponse("Order successfully cancelled.", cancelledOrder);
    }

    private OrderDto toOrderDto(OrderServiceOrderResponse order) {
        return new OrderDto(
                order.orderId().toString(),
                order.platformId().toString(),
                order.platformUserId().toString(),
                order.instrumentType(),
                order.instrumentId(),
                order.orderType(),
                order.side(),
                order.quantity(),
                order.limitPrice(),
                order.status(),
                order.filledQuantity(),
                order.averageFillPrice(),
                order.exchangeFee(),
                order.createdAt(),
                order.updatedAt(),
                order.expiresAt()
        );
    }

    private void validatePlaceOrder(PlaceOrderRequest request) {
        if ("LIMIT".equalsIgnoreCase(request.orderType()) && request.limitPrice() == null) {
            throw new ValidationException("limit_price is required when order_type is LIMIT.");
        }
        if ("MARKET".equalsIgnoreCase(request.orderType()) && request.marketPrice() == null) {
            throw new ValidationException("market_price is required when order_type is MARKET.");
        }
    }

    private record OrderServiceOrderRequest(
            UUID orderId,
            UUID platformId,
            UUID platformUserId,
            String instrumentType,
            String instrumentId,
            String orderType,
            String side,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String status,
            BigDecimal filledQuantity,
            BigDecimal averageFillPrice,
            BigDecimal exchangeFee,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime expiresAt
    ) {}

    private record OrderServiceOrderResponse(
            UUID orderId,
            UUID platformId,
            UUID platformUserId,
            String instrumentType,
            String instrumentId,
            String orderType,
            String side,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String status,
            BigDecimal filledQuantity,
            BigDecimal averageFillPrice,
            BigDecimal exchangeFee,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime expiresAt
    ) {}
}