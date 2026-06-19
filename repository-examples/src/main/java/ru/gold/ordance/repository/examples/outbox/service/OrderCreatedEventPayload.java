package ru.gold.ordance.repository.examples.outbox.service;

import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderCreatedEventPayload(
        int orderId,
        int userId,
        String productName,
        BigDecimal amount,
        OffsetDateTime createdAt
) {

    public static OrderCreatedEventPayload from(Order order) {
        return new OrderCreatedEventPayload(
                order.getOrderId(),
                order.getUserId(),
                order.getProductName(),
                order.getAmount(),
                order.getCreatedAt()
        );
    }
}
