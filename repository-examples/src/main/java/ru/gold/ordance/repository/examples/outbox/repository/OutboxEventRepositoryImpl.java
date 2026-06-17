package ru.gold.ordance.repository.examples.outbox.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventPayloadSerializer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String EVENT_TYPE = "OrderCreated";

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
                claim_until = :claimUntil,
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
            """;

    private static final String MARK_FAILED = """
            UPDATE outbox_events
            SET status = 'FAILED',
                last_error = :lastError,
                next_attempt_at = :nextAttemptAt,
                claimed_at = NULL,
                claim_until = NULL
            WHERE event_id = :eventId
            """;

    private final JdbcClient jdbc;
    private final OutboxEventPayloadSerializer payloadSerializer;

    public OutboxEventRepositoryImpl(JdbcClient jdbc, OutboxEventPayloadSerializer payloadSerializer) {
        this.jdbc = Asserts.nonNull(jdbc, "jdbc");
        this.payloadSerializer = Asserts.nonNull(payloadSerializer, "payloadSerializer");
    }

    @Override
    public void save(Order order) {
        OutboxEvent event = toOutboxEvent(order);
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
    public List<OutboxEvent> claimBatch(int batchSize, OffsetDateTime claimUntil) {
        Asserts.positive(batchSize, "batchSize");
        Asserts.nonNull(claimUntil, "claimUntil");
        return jdbc.sql(CLAIM_BATCH)
                .param("batchSize", batchSize)
                .param("claimUntil", claimUntil)
                .query((rs, rowNum) -> mapEvent(rs))
                .list();
    }

    @Override
    public void markProcessed(UUID eventId) {
        jdbc.sql(MARK_PROCESSED)
                .param("eventId", eventId)
                .update();
    }

    @Override
    public void markFailed(UUID eventId, String lastError, OffsetDateTime nextAttemptAt) {
        jdbc.sql(MARK_FAILED)
                .param("eventId", eventId)
                .param("lastError", lastError)
                .param("nextAttemptAt", nextAttemptAt)
                .update();
    }

    private OutboxEvent toOutboxEvent(Order order) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateId(String.valueOf(order.getOrderId()));
        event.setEventType(EVENT_TYPE);
        event.setPayload(payloadSerializer.serialize(order));
        return event;
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
        event.setAttemptCount(rs.getInt("attempt_count"));
        event.setLastError(rs.getString("last_error"));
        event.setNextAttemptAt(rs.getObject("next_attempt_at", OffsetDateTime.class));
        return event;
    }
}
