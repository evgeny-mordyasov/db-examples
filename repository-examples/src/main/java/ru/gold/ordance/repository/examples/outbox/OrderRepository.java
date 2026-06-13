package ru.gold.ordance.repository.examples.outbox;

import ru.gold.ordance.jdbc.examples.common.db.model.Order;

public interface OrderRepository {

    Order save(Order order);
}
