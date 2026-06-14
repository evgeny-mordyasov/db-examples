package ru.gold.ordance.repository.examples.outbox;

import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public class OutboxEventRelay {

    private final TransactionTemplate transactionTemplate;
    private final OutboxEventRepository repository;
    private final OutboxEventConsumer consumer;
    private final Properties properties;
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
        this(transactionTemplate, repository, consumer, Properties.defaults(), clock);
    }

    public OutboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository repository,
            OutboxEventConsumer consumer,
            Properties properties
    ) {
        this(transactionTemplate, repository, consumer, properties, Clock.systemDefaultZone());
    }

    public OutboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository repository,
            OutboxEventConsumer consumer,
            Properties properties,
            Clock clock
    ) {
        this.transactionTemplate = Asserts.nonNull(transactionTemplate, "transactionTemplate");
        this.repository = Asserts.nonNull(repository, "repository");
        this.consumer = Asserts.nonNull(consumer, "consumer");
        this.properties = Asserts.nonNull(properties, "properties");
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
        long attempts = Math.max(1, event.getAttemptCount() + 1L);
        return OffsetDateTime.now(clock).plus(properties.nextAttemptDelay().multipliedBy(attempts));
    }

    private String truncate(String value) {
        if (value == null || value.length() <= properties.maxErrorLength()) {
            return value;
        }
        return value.substring(0, properties.maxErrorLength());
    }

    public record PollResult(int fetched, int delivered, int failed) {
    }

    public record Properties(int maxErrorLength, Duration nextAttemptDelay) {

        private static final int DEFAULT_MAX_ERROR_LENGTH = 500;
        private static final Duration DEFAULT_NEXT_ATTEMPT_DELAY = Duration.ofSeconds(1);

        public Properties {
            Asserts.positive(maxErrorLength, "maxErrorLength");
            Asserts.nonNull(nextAttemptDelay, "nextAttemptDelay");
            if (nextAttemptDelay.isZero() || nextAttemptDelay.isNegative()) {
                throw new IllegalArgumentException("[nextAttemptDelay] must be a positive duration");
            }
        }

        public static Properties defaults() {
            return new Properties(DEFAULT_MAX_ERROR_LENGTH, DEFAULT_NEXT_ATTEMPT_DELAY);
        }
    }
}
