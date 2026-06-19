package ru.gold.ordance.repository.examples.outbox.repository;

import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {

    void save(OutboxEvent event);

    List<OutboxEvent> claimBatch(int batchSize, Duration processingTimeout);

    boolean markProcessed(UUID eventId, OffsetDateTime claimUntil);

    boolean markFailed(UUID eventId, OffsetDateTime claimUntil, String lastError, Duration nextAttemptDelay, int maxAttempts);
}
