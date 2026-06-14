package ru.gold.ordance.repository.examples.outbox;

import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

public class OutboxEventRelay {

    private static final int MAX_ERROR_LENGTH = 500;

    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository repository;
    private final OutboxEventConsumer consumer;
    private final Clock clock;

    public OutboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository repository,
            OutboxEventConsumer consumer
    ) {
        this(transactionTemplate, repository, consumer, Clock.systemDefaultZone());
    }

    public OutboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository repository,
            OutboxEventConsumer consumer,
            Clock clock
    ) {
        this.transactionTemplate = Asserts.nonNull(transactionTemplate, "transactionTemplate");
        this.repository = Asserts.nonNull(repository, "repository");
        this.consumer = Asserts.nonNull(consumer, "consumer");
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
        long delaySeconds = Math.max(1, event.getAttemptCount() + 1L);
        return OffsetDateTime.now(clock).plusSeconds(delaySeconds);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    public record PollResult(int fetched, int delivered, int failed) {
    }
}
