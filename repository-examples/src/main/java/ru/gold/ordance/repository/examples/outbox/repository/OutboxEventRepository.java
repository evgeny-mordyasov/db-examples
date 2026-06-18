package ru.gold.ordance.repository.examples.outbox.repository;

import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository {

    void save(Order order);

    List<OutboxEvent> claimBatch(int batchSize, OffsetDateTime claimUntil);

    boolean markProcessed(UUID eventId, OffsetDateTime claimUntil);

    boolean markFailed(UUID eventId, OffsetDateTime claimUntil, String lastError, OffsetDateTime nextAttemptAt, int maxAttempts);
}
