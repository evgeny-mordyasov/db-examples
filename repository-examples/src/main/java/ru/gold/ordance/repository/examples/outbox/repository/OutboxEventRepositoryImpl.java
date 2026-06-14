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

    private static final String FIND_UNPROCESSED_BATCH = """
            SELECT event_id,
                   aggregate_type,
                   aggregate_id,
                   event_type,
                   payload::text AS payload,
                   created_at,
                   processed_at,
                   attempt_count,
                   last_error,
                   next_attempt_at
            FROM outbox_events
            WHERE processed_at IS NULL
              AND next_attempt_at <= now()
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_PROCESSED = """
            UPDATE outbox_events
            SET processed_at = now(),
                last_error = NULL
            WHERE event_id = :eventId
            """;

    private static final String MARK_FAILED = """
            UPDATE outbox_events
            SET attempt_count = attempt_count + 1,
                last_error = :lastError,
                next_attempt_at = :nextAttemptAt
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
    public List<OutboxEvent> findUnprocessedBatch(int batchSize) {
        Asserts.positive(batchSize, "batchSize");
        return jdbc.sql(FIND_UNPROCESSED_BATCH)
                .param("batchSize", batchSize)
                .query((rs, rowNum) -> {
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
                })
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
}
