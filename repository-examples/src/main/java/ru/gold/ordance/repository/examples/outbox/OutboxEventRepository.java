package ru.gold.ordance.repository.examples.outbox;

import ru.gold.ordance.jdbc.examples.common.db.model.Order;

public interface OutboxEventRepository {

    void save(Order order);
}
