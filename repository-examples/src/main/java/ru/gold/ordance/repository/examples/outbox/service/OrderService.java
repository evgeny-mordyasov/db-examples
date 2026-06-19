package ru.gold.ordance.repository.examples.outbox.service;

import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;

import java.util.UUID;

public class OrderService {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String EVENT_TYPE = "OrderCreated";

    private final TransactionTemplate tx;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPayloadSerializer payloadSerializer;

    public OrderService(
            TransactionTemplate tx,
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            OutboxEventPayloadSerializer payloadSerializer
    ) {
        this.tx = Asserts.nonNull(tx, "tx");
        this.orderRepository = Asserts.nonNull(orderRepository, "orderRepository");
        this.outboxEventRepository = Asserts.nonNull(outboxEventRepository, "outboxEventRepository");
        this.payloadSerializer = Asserts.nonNull(payloadSerializer, "payloadSerializer");
    }

    public Order createOrder(Order order) {
        return tx.execute(status -> {
            Order savedOrder = orderRepository.save(order);
            outboxEventRepository.save(toOutboxEvent(savedOrder));
            return savedOrder;
        });
    }

    private OutboxEvent toOutboxEvent(Order order) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID());
        event.setAggregateType(AGGREGATE_TYPE);
        event.setAggregateId(String.valueOf(order.getOrderId()));
        event.setEventType(EVENT_TYPE);
        event.setPayload(payloadSerializer.serialize(OrderCreatedEventPayload.from(order)));
        return event;
    }
}
