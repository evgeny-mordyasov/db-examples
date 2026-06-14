package ru.gold.ordance.repository.examples.outbox;

import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

public interface OutboxEventConsumer {

    void accept(OutboxEvent event);
}
