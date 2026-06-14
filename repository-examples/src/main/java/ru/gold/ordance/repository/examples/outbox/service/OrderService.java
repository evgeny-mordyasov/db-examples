package ru.gold.ordance.repository.examples.outbox.service;

import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;

public class OrderService {

    private final TransactionTemplate tx;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    public OrderService(
            TransactionTemplate tx,
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.tx = Asserts.nonNull(tx, "tx");
        this.orderRepository = Asserts.nonNull(orderRepository, "orderRepository");
        this.outboxEventRepository = Asserts.nonNull(outboxEventRepository, "outboxEventRepository");
    }

    public Order createOrder(Order order) {
        return tx.execute(status -> {
            Order savedOrder = orderRepository.save(order);
            outboxEventRepository.save(savedOrder);
            return savedOrder;
        });
    }
}
