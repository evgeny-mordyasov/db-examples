package ru.gold.ordance.jdbc.examples.common.db.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void gettersAndSetters_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        LocalDateTime processedAt = LocalDateTime.of(2026, 1, 2, 3, 5);
        OutboxEvent event = new OutboxEvent();

        event.setEventId(eventId);
        event.setAggregateType("Order");
        event.setAggregateId("11");
        event.setEventType("OrderCreated");
        event.setPayload("{\"orderId\":11}");
        event.setCreatedAt(createdAt);
        event.setProcessedAt(processedAt);

        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getAggregateType()).isEqualTo("Order");
        assertThat(event.getAggregateId()).isEqualTo("11");
        assertThat(event.getEventType()).isEqualTo("OrderCreated");
        assertThat(event.getPayload()).isEqualTo("{\"orderId\":11}");
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
        assertThat(event.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    void toString_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("Order");
        event.setAggregateId("11");
        event.setEventType("OrderCreated");
        event.setPayload("{\"orderId\":11}");
        event.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        event.setProcessedAt(null);

        assertThat(event.toString())
                .isEqualTo("OutboxEvent{eventId=7a2517f2-e651-4569-9f96-ac09f8b64f9a, aggregateType='Order', aggregateId='11', eventType='OrderCreated', payload='{\"orderId\":11}', createdAt=2026-01-02T03:04, processedAt=null}");
    }
}
