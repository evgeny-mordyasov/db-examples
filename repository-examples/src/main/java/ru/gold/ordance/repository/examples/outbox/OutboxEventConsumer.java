package ru.gold.ordance.repository.examples.outbox;

import jakarta.annotation.Nonnull;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

public interface OutboxEventConsumer {

    void accept(@Nonnull OutboxEvent event);
}
