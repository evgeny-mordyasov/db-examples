package ru.gold.ordance.repository.examples.outbox.service;

import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public class OutboxEventRelay {

    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository repository;
    private final OutboxEventConsumer consumer;
    private final OutboxEventRelayProperties properties;
    private final Clock clock;

    public OutboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository repository,
            OutboxEventConsumer consumer,
            OutboxEventRelayProperties properties,
            Clock clock
    ) {
        this.transactionTemplate = Asserts.nonNull(transactionTemplate, "transactionTemplate");
        this.repository = Asserts.nonNull(repository, "repository");
        this.consumer = Asserts.nonNull(consumer, "consumer");
        this.properties = Asserts.nonNull(properties, "properties");
        Asserts.positive(properties.getMaxErrorLength(), "maxErrorLength");
        Asserts.nonNull(properties.getNextAttemptDelay(), "nextAttemptDelay");
        this.clock = Asserts.nonNull(clock, "clock");
    }

    public PollResult pollBatch(int batchSize) {
        Asserts.positive(batchSize, "batchSize");
        return transactionTemplate.execute(status -> processBatch(batchSize));
    }

    private PollResult processBatch(int batchSize) {
        List<OutboxEvent> events = repository.findUnprocessedBatch(batchSize);
        int delivered = 0;
        int failed = 0;

        for (OutboxEvent event : events) {
            try {
                consumer.accept(event);
                repository.markProcessed(event.getEventId());
                delivered++;
            } catch (RuntimeException e) {
                repository.markFailed(
                        event.getEventId(),
                        truncate(e.getMessage()),
                        nextAttemptAt(event)
                );
                failed++;
            }
        }

        return new PollResult(events.size(), delivered, failed);
    }

    private OffsetDateTime nextAttemptAt(OutboxEvent event) {
        long nextAttemptNumber = event.getAttemptCount() + 1L;
        Duration delay = properties.getNextAttemptDelay().multipliedBy(nextAttemptNumber);
        return OffsetDateTime.now(clock).plus(delay);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= properties.getMaxErrorLength()) {
            return value;
        }
        return value.substring(0, properties.getMaxErrorLength());
    }

    public record PollResult(int fetched, int delivered, int failed) {
    }
}
