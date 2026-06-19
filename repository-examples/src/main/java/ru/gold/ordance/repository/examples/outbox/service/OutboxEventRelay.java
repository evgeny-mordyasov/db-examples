package ru.gold.ordance.repository.examples.outbox.service;

import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;

import java.util.List;

public class OutboxEventRelay {

    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository repository;
    private final OutboxEventConsumer consumer;
    private final OutboxEventRelayProperties properties;

    public OutboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository repository,
            OutboxEventConsumer consumer,
            OutboxEventRelayProperties properties
    ) {
        this.transactionTemplate = Asserts.nonNull(transactionTemplate, "transactionTemplate");
        this.repository = Asserts.nonNull(repository, "repository");
        this.consumer = Asserts.nonNull(consumer, "consumer");
        this.properties = Asserts.nonNull(properties, "properties");
        Asserts.positive(properties.getMaxErrorLength(), "maxErrorLength");
        Asserts.positive(properties.getMaxAttempts(), "maxAttempts");
        Asserts.positive(properties.getNextAttemptDelay(), "nextAttemptDelay");
        Asserts.positive(properties.getProcessingTimeout(), "processingTimeout");
    }

    public PollResult pollBatch(int batchSize) {
        Asserts.positive(batchSize, "batchSize");
        return processBatch(batchSize);
    }

    private PollResult processBatch(int batchSize) {
        List<OutboxEvent> events = transactionTemplate.execute(
                status -> repository.claimBatch(batchSize, properties.getProcessingTimeout())
        );
        int delivered = 0;
        int failed = 0;

        for (OutboxEvent event : events) {
            try {
                consumer.accept(event);
            } catch (RuntimeException e) {
                if (markFailed(event, e)) {
                    failed++;
                }
                continue;
            }
            if (markProcessed(event)) {
                delivered++;
            }
        }

        return new PollResult(events.size(), delivered, failed);
    }

    private boolean markProcessed(OutboxEvent event) {
        return transactionTemplate.execute(status -> repository.markProcessed(event.getEventId(), event.getClaimUntil()));
    }

    private boolean markFailed(OutboxEvent event, RuntimeException e) {
        return transactionTemplate.execute(status -> repository.markFailed(
                        event.getEventId(),
                        event.getClaimUntil(),
                        truncate(e.getMessage()),
                        properties.getNextAttemptDelay(),
                        properties.getMaxAttempts()
                ));
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
