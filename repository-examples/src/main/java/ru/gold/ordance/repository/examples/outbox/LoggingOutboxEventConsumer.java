package ru.gold.ordance.repository.examples.outbox;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

public class LoggingOutboxEventConsumer implements OutboxEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutboxEventConsumer.class);

    @Override
    public void accept(@Nonnull OutboxEvent event) {
        LOGGER.info("Delivered outbox event: {}", event);
    }
}
