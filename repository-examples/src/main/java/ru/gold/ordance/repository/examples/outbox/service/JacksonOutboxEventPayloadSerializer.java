package ru.gold.ordance.repository.examples.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class JacksonOutboxEventPayloadSerializer implements OutboxEventPayloadSerializer {

    private final ObjectMapper objectMapper;

    public JacksonOutboxEventPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Asserts.nonNull(objectMapper, "objectMapper");
    }

    @Override
    public String serialize(Order order) {
        try {
            return objectMapper.writeValueAsString(OrderCreatedEvent.from(order));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload.", e);
        }
    }
}

record OrderCreatedEvent(
        int orderId,
        int userId,
        String productName,
        BigDecimal amount,
        OffsetDateTime createdAt
) {

    static OrderCreatedEvent from(Order order) {
        return new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getProductName(),
                order.getAmount(),
                order.getCreatedAt()
        );
    }
}
