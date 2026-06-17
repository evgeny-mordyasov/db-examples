package ru.gold.ordance.jdbc.examples.common.db.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void gettersAndSetters_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-02T03:04:00+03:00");
        OffsetDateTime processedAt = OffsetDateTime.parse("2026-01-02T03:05:00+03:00");
        OffsetDateTime claimedAt = OffsetDateTime.parse("2026-01-02T03:04:10+03:00");
        OffsetDateTime claimUntil = OffsetDateTime.parse("2026-01-02T03:04:30+03:00");
        OffsetDateTime nextAttemptAt = OffsetDateTime.parse("2026-01-02T03:06:00+03:00");
        OutboxEvent event = new OutboxEvent();

        event.setEventId(eventId);
        event.setAggregateType("Order");
        event.setAggregateId("11");
        event.setEventType("OrderCreated");
        event.setPayload("{\"orderId\":11}");
        event.setCreatedAt(createdAt);
        event.setProcessedAt(processedAt);
        event.setStatus("PROCESSING");
        event.setClaimedAt(claimedAt);
        event.setClaimUntil(claimUntil);
        event.setAttemptCount(2);
        event.setLastError("Network error");
        event.setNextAttemptAt(nextAttemptAt);

        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getAggregateType()).isEqualTo("Order");
        assertThat(event.getAggregateId()).isEqualTo("11");
        assertThat(event.getEventType()).isEqualTo("OrderCreated");
        assertThat(event.getPayload()).isEqualTo("{\"orderId\":11}");
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
        assertThat(event.getProcessedAt()).isEqualTo(processedAt);
        assertThat(event.getStatus()).isEqualTo("PROCESSING");
        assertThat(event.getClaimedAt()).isEqualTo(claimedAt);
        assertThat(event.getClaimUntil()).isEqualTo(claimUntil);
        assertThat(event.getAttemptCount()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("Network error");
        assertThat(event.getNextAttemptAt()).isEqualTo(nextAttemptAt);
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
        event.setCreatedAt(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
        event.setProcessedAt(null);
        event.setStatus("PROCESSING");
        event.setClaimedAt(OffsetDateTime.parse("2026-01-02T03:04:10+03:00"));
        event.setClaimUntil(OffsetDateTime.parse("2026-01-02T03:04:30+03:00"));
        event.setAttemptCount(1);
        event.setLastError("Timeout");
        event.setNextAttemptAt(OffsetDateTime.parse("2026-01-02T03:06:00+03:00"));

        assertThat(event.toString())
                .isEqualTo("OutboxEvent{eventId=7a2517f2-e651-4569-9f96-ac09f8b64f9a, aggregateType='Order', aggregateId='11', eventType='OrderCreated', payload='{\"orderId\":11}', createdAt=2026-01-02T03:04+03:00, processedAt=null, status='PROCESSING', claimedAt=2026-01-02T03:04:10+03:00, claimUntil=2026-01-02T03:04:30+03:00, attemptCount=1, lastError='Timeout', nextAttemptAt=2026-01-02T03:06+03:00}");
    }
}
