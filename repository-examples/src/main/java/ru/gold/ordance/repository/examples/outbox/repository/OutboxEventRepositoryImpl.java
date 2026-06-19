package ru.gold.ordance.repository.examples.outbox.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private static final String INSERT_EVENT = """
            INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, event_type, payload)
            VALUES (:eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb))
            """;

    private static final String CLAIM_BATCH = """
            WITH claimable AS (
                SELECT event_id
                FROM outbox_events
                WHERE processed_at IS NULL
                  AND (
                      (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now())
                      OR (status = 'PROCESSING' AND claim_until <= now())
                  )
                ORDER BY created_at, event_id
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_events event
            SET status = 'PROCESSING',
                claimed_at = now(),
                claim_until = now() + (:processingTimeoutMillis * interval '1 millisecond'),
                attempt_count = attempt_count + 1,
                last_error = NULL
            FROM claimable
            WHERE event.event_id = claimable.event_id
            RETURNING event.event_id,
                      event.aggregate_type,
                      event.aggregate_id,
                      event.event_type,
                      event.payload::text AS payload,
                      event.created_at,
                      event.processed_at,
                      event.status,
                      event.claimed_at,
                      event.claim_until,
                      event.attempt_count,
                      event.last_error,
                      event.next_attempt_at
            """;

    private static final String MARK_PROCESSED = """
            UPDATE outbox_events
            SET status = 'PROCESSED',
                processed_at = now(),
                last_error = NULL,
                claimed_at = NULL,
                claim_until = NULL
            WHERE event_id = :eventId
              AND status = 'PROCESSING'
              AND claim_until = :claimUntil
            """;

    private static final String MARK_FAILED = """
            UPDATE outbox_events
            SET status = CASE
                    WHEN attempt_count >= :maxAttempts THEN 'FINAL_FAILED'
                    ELSE 'FAILED'
                END,
                last_error = :lastError,
                next_attempt_at = now() + (:nextAttemptDelayMillis * attempt_count * interval '1 millisecond'),
                claimed_at = NULL,
                claim_until = NULL
            WHERE event_id = :eventId
              AND status = 'PROCESSING'
              AND claim_until = :claimUntil
            """;

    private final JdbcClient jdbc;

    public OutboxEventRepositoryImpl(JdbcClient jdbc) {
        this.jdbc = Asserts.nonNull(jdbc, "jdbc");
    }

    @Override
    public void save(OutboxEvent event) {
        Map<String, Object> params = Map.of(
                "eventId", event.getEventId(),
                "aggregateType", event.getAggregateType(),
                "aggregateId", event.getAggregateId(),
                "eventType", event.getEventType(),
                "payload", event.getPayload()
        );
        jdbc.sql(INSERT_EVENT)
                .params(params)
                .update();
    }

    @Override
    public List<OutboxEvent> claimBatch(int batchSize, Duration processingTimeout) {
        Asserts.positive(batchSize, "batchSize");
        Asserts.positive(processingTimeout, "processingTimeout");
        return jdbc.sql(CLAIM_BATCH)
                .param("batchSize", batchSize)
                .param("processingTimeoutMillis", processingTimeout.toMillis())
                .query((rs, rowNum) -> mapEvent(rs))
                .list();
    }

    @Override
    public boolean markProcessed(UUID eventId, OffsetDateTime claimUntil) {
        return jdbc.sql(MARK_PROCESSED)
                .param("eventId", eventId)
                .param("claimUntil", claimUntil)
                .update() == 1;
    }

    @Override
    public boolean markFailed(
            UUID eventId,
            OffsetDateTime claimUntil,
            String lastError,
            Duration nextAttemptDelay,
            int maxAttempts
    ) {
        Asserts.positive(nextAttemptDelay, "nextAttemptDelay");
        Asserts.positive(maxAttempts, "maxAttempts");
        return jdbc.sql(MARK_FAILED)
                .param("eventId", eventId)
                .param("claimUntil", claimUntil)
                .param("lastError", lastError)
                .param("nextAttemptDelayMillis", nextAttemptDelay.toMillis())
                .param("maxAttempts", maxAttempts)
                .update() == 1;
    }

    private static OutboxEvent mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(rs.getObject("event_id", UUID.class));
        event.setAggregateType(rs.getString("aggregate_type"));
        event.setAggregateId(rs.getString("aggregate_id"));
        event.setEventType(rs.getString("event_type"));
        event.setPayload(rs.getString("payload"));
        event.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        event.setProcessedAt(rs.getObject("processed_at", OffsetDateTime.class));
        event.setStatus(rs.getString("status"));
        event.setClaimedAt(rs.getObject("claimed_at", OffsetDateTime.class));
        event.setClaimUntil(rs.getObject("claim_until", OffsetDateTime.class));
        event.setAttemptCount(rs.getInt("attempt_count"));
        event.setLastError(rs.getString("last_error"));
        event.setNextAttemptAt(rs.getObject("next_attempt_at", OffsetDateTime.class));
        return event;
    }
}
