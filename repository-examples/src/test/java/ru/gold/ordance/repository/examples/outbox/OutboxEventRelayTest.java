package ru.gold.ordance.repository.examples.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventConsumer;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventRelay;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelayTest {

    @Mock private TransactionTemplate tx;
    @Mock private OutboxEventRepository repository;
    @Mock private OutboxEventConsumer consumer;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:00Z"), ZoneOffset.UTC);

    @Test
    void createInstance_transactionTemplateIsNull() {
        assertThatThrownBy(() -> newRelay(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[transactionTemplate] must not be null.");
    }

    @Test
    void pollBatch_batchSizeIsZero() {
        OutboxEventRelay relay = newRelay(tx);

        assertThatThrownBy(() -> relay.pollBatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[batchSize] must be a positive number");
        verifyNoInteractions(tx, repository, consumer);
    }

    @Test
    void pollBatch_successAndFailure() {
        OutboxEvent success = event("7a2517f2-e651-4569-9f96-ac09f8b64f9a", 1);
        OutboxEvent failure = event("23eb899d-ddf1-4687-a6fe-8231dbf5f383", 2);
        stubTransaction();
        when(repository.claimBatch(10, OffsetDateTime.parse("2026-01-02T03:04:30Z"))).thenReturn(List.of(success, failure));
        when(repository.markProcessed(success.getEventId(), success.getClaimUntil())).thenReturn(true);
        when(repository.markFailed(
                failure.getEventId(),
                failure.getClaimUntil(),
                "Delivery timeout",
                OffsetDateTime.parse("2026-01-02T03:04:02Z")
        )).thenReturn(true);
        failForEvent(failure, "Delivery timeout");

        OutboxEventRelay relay = newRelay(tx);

        OutboxEventRelay.PollResult result = relay.pollBatch(10);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.delivered()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(repository).claimBatch(10, OffsetDateTime.parse("2026-01-02T03:04:30Z"));
        verify(consumer).accept(success);
        verify(consumer).accept(failure);
        verify(repository).markProcessed(success.getEventId(), success.getClaimUntil());
        verify(repository).markFailed(
                failure.getEventId(),
                failure.getClaimUntil(),
                "Delivery timeout",
                OffsetDateTime.parse("2026-01-02T03:04:02Z")
        );
    }

    @Test
    void pollBatch_truncatesLongError() {
        OutboxEvent failure = event("23eb899d-ddf1-4687-a6fe-8231dbf5f383", 1);
        stubTransaction();
        when(repository.claimBatch(1, OffsetDateTime.parse("2026-01-02T03:04:30Z"))).thenReturn(List.of(failure));
        when(repository.markFailed(
                any(UUID.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(OffsetDateTime.class)
        )).thenReturn(true);
        failForEvent(failure, "x".repeat(600));

        OutboxEventRelay relay = newRelay(tx);

        relay.pollBatch(1);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).markFailed(
                any(UUID.class),
                any(OffsetDateTime.class),
                errorCaptor.capture(),
                any(OffsetDateTime.class)
        );
        assertThat(errorCaptor.getValue()).hasSize(500);
    }

    @Test
    void pollBatch_usesConfiguredMaxErrorLength() {
        OutboxEvent failure = event("23eb899d-ddf1-4687-a6fe-8231dbf5f383", 1);
        stubTransaction();
        when(repository.claimBatch(1, OffsetDateTime.parse("2026-01-02T03:04:30Z"))).thenReturn(List.of(failure));
        when(repository.markFailed(
                any(UUID.class),
                any(OffsetDateTime.class),
                any(String.class),
                any(OffsetDateTime.class)
        )).thenReturn(true);
        failForEvent(failure, "x".repeat(50));

        OutboxEventRelay relay = new OutboxEventRelay(
                tx,
                repository,
                consumer,
                properties(10, Duration.ofSeconds(1)),
                clock
        );

        relay.pollBatch(1);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).markFailed(
                any(UUID.class),
                any(OffsetDateTime.class),
                errorCaptor.capture(),
                any(OffsetDateTime.class)
        );
        assertThat(errorCaptor.getValue()).hasSize(10);
    }

    @Test
    void pollBatch_usesConfiguredNextAttemptDelay() {
        OutboxEvent failure = event("23eb899d-ddf1-4687-a6fe-8231dbf5f383", 2);
        stubTransaction();
        when(repository.claimBatch(1, OffsetDateTime.parse("2026-01-02T03:04:30Z"))).thenReturn(List.of(failure));
        when(repository.markFailed(
                failure.getEventId(),
                failure.getClaimUntil(),
                "Delivery timeout",
                OffsetDateTime.parse("2026-01-02T03:08:00Z")
        )).thenReturn(true);
        failForEvent(failure, "Delivery timeout");

        OutboxEventRelay relay = new OutboxEventRelay(
                tx,
                repository,
                consumer,
                properties(500, Duration.ofMinutes(2)),
                clock
        );

        relay.pollBatch(1);

        verify(repository).markFailed(
                failure.getEventId(),
                failure.getClaimUntil(),
                "Delivery timeout",
                OffsetDateTime.parse("2026-01-02T03:08:00Z")
        );
    }

    @Test
    void pollBatch_staleProcessedUpdate_shouldNotCountDelivered() {
        OutboxEvent stale = event("7a2517f2-e651-4569-9f96-ac09f8b64f9a", 1);
        stubTransaction();
        when(repository.claimBatch(1, OffsetDateTime.parse("2026-01-02T03:04:30Z"))).thenReturn(List.of(stale));
        when(repository.markProcessed(stale.getEventId(), stale.getClaimUntil())).thenReturn(false);

        OutboxEventRelay relay = newRelay(tx);

        OutboxEventRelay.PollResult result = relay.pollBatch(1);

        assertThat(result).isEqualTo(new OutboxEventRelay.PollResult(1, 0, 0));
        verify(repository).markProcessed(stale.getEventId(), stale.getClaimUntil());
    }

    @Test
    void pollBatch_usesConfiguredProcessingTimeout() {
        stubTransaction();
        when(repository.claimBatch(1, OffsetDateTime.parse("2026-01-02T03:04:05Z"))).thenReturn(List.of());

        OutboxEventRelay relay = new OutboxEventRelay(
                tx,
                repository,
                consumer,
                properties(500, Duration.ofSeconds(1), Duration.ofSeconds(5)),
                clock
        );

        relay.pollBatch(1);

        verify(repository).claimBatch(1, OffsetDateTime.parse("2026-01-02T03:04:05Z"));
    }

    @SuppressWarnings("unchecked")
    private void stubTransaction() {
        doAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(tx).execute(any(TransactionCallback.class));
    }

    private void failForEvent(OutboxEvent expectedEvent, String message) {
        doAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            if (event.getEventId().equals(expectedEvent.getEventId())) {
                throw new RuntimeException(message);
            }
            return null;
        }).when(consumer).accept(any(OutboxEvent.class));
    }

    private OutboxEventRelay newRelay(TransactionTemplate transactionTemplate) {
        return new OutboxEventRelay(
                transactionTemplate,
                repository,
                consumer,
                properties(500, Duration.ofSeconds(1)),
                clock
        );
    }

    private static OutboxEventRelayProperties properties(int maxErrorLength, Duration nextAttemptDelay) {
        OutboxEventRelayProperties properties = new OutboxEventRelayProperties();
        properties.setMaxErrorLength(maxErrorLength);
        properties.setNextAttemptDelay(nextAttemptDelay);
        properties.setProcessingTimeout(Duration.ofSeconds(30));
        return properties;
    }

    private static OutboxEventRelayProperties properties(
            int maxErrorLength,
            Duration nextAttemptDelay,
            Duration processingTimeout
    ) {
        OutboxEventRelayProperties properties = properties(maxErrorLength, nextAttemptDelay);
        properties.setProcessingTimeout(processingTimeout);
        return properties;
    }

    private static OutboxEvent event(String eventId, int attemptCount) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.fromString(eventId));
        event.setAggregateType("Order");
        event.setAggregateId("11");
        event.setEventType("OrderCreated");
        event.setPayload("{\"orderId\":11}");
        event.setAttemptCount(attemptCount);
        event.setClaimUntil(OffsetDateTime.parse("2026-01-02T03:04:30Z"));
        return event;
    }
}
