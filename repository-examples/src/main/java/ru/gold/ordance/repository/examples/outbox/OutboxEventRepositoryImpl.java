package ru.gold.ordance.repository.examples.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.util.Map;
import java.util.UUID;

public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String EVENT_TYPE = "OrderCreated";

    private static final String INSERT_EVENT = """
            INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, event_type, payload)
            VALUES (:eventId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb))
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
