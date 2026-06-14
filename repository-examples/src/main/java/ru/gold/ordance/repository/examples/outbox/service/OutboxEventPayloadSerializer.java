package ru.gold.ordance.repository.examples.outbox.service;

import ru.gold.ordance.jdbc.examples.common.db.model.Order;

public interface OutboxEventPayloadSerializer {

    String serialize(Order order);
}
