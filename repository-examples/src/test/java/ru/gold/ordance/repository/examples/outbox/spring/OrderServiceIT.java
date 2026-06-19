package ru.gold.ordance.repository.examples.outbox.spring;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.testcontainers.Containers;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.service.OrderService;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventRelay;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles({"test", "outbox"})
@Import(Containers.class)
class OrderServiceIT {

    @Autowired private JdbcClient jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private OutboxEventRelay outboxEventRelay;
    @Autowired private OutboxEventPayloadSerializer payloadSerializer;
    @Autowired private OrderService service;
    @Autowired private Flyway flyway;

    @BeforeEach
    void setUp() {
        flyway.clean();
        flyway.migrate();
        seedUser(7);
    }

    @Test
    void createOrder_commitsOrderAndOutboxEvent() {
        Order savedOrder = service.createOrder(newOrder());

        assertThat(countRows("orders")).isEqualTo(1);
        assertThat(countRows("outbox_events")).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT aggregate_type, aggregate_id, event_type
                        FROM outbox_events
                        """)
                .query((rs, rowNum) -> new OutboxEventRow(
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("event_type")
                ))
                .single())
                .isEqualTo(new OutboxEventRow("Order", String.valueOf(savedOrder.getOrderId()), "OrderCreated"));
        assertThat(unprocessedEventCount()).isEqualTo(1);
    }

    @Test
    void outboxRelay_marksEventProcessed() {
        service.createOrder(newOrder());

        OutboxEventRelay.PollResult result = outboxEventRelay.pollBatch(10);

        assertThat(result).isEqualTo(new OutboxEventRelay.PollResult(1, 1, 0));
        assertThat(unprocessedEventCount()).isZero();
        assertThat(jdbc.sql("""
                        SELECT status,
                               processed_at IS NOT NULL AS processed,
                               claimed_at,
                               claim_until
                        FROM outbox_events
                        """)
                .query((rs, rowNum) -> new ProcessedRow(
                        rs.getString("status"),
                        rs.getBoolean("processed"),
                        rs.getObject("claimed_at", OffsetDateTime.class),
                        rs.getObject("claim_until", OffsetDateTime.class)
                ))
                .single())
                .isEqualTo(new ProcessedRow("PROCESSED", true, null, null));
    }

    @Test
    void outboxRelay_recordsFailureAndRetryState() {
        service.createOrder(newOrder());
        OutboxEventRelay failingRelay = new OutboxEventRelay(
                tx,
                outboxEventRepository,
                event -> {
                    throw new RuntimeException("Consumer failed");
                },
                properties(500, Duration.ofSeconds(1))
        );

        OutboxEventRelay.PollResult result = failingRelay.pollBatch(10);

        assertThat(result).isEqualTo(new OutboxEventRelay.PollResult(1, 0, 1));
        Map<String, Object> row = jdbc.sql("""
                        SELECT processed_at,
                               status,
                               attempt_count,
                               last_error,
                               next_attempt_at,
                               claimed_at,
                               claim_until
                        FROM outbox_events
                        """)
                .query((rs, rowNum) -> {
                    Map<String, Object> values = new LinkedHashMap<>();
                    values.put("processed_at", rs.getObject("processed_at", OffsetDateTime.class));
                    values.put("status", rs.getString("status"));
                    values.put("attempt_count", rs.getInt("attempt_count"));
                    values.put("last_error", rs.getString("last_error"));
                    values.put("next_attempt_at", rs.getObject("next_attempt_at", OffsetDateTime.class));
                    values.put("claimed_at", rs.getObject("claimed_at", OffsetDateTime.class));
                    values.put("claim_until", rs.getObject("claim_until", OffsetDateTime.class));
                    return values;
                })
                .single();
        assertThat(row)
                .containsEntry("status", "FAILED")
                .containsEntry("attempt_count", 1)
                .containsEntry("last_error", "Consumer failed");
        assertThat(row.get("next_attempt_at")).isInstanceOf(OffsetDateTime.class);
        assertThat(row.get("processed_at")).isNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("claim_until")).isNull();
    }

    @Test
    void outboxRelay_marksFinalFailedAfterMaxAttemptsAndSkipsRetry() {
        service.createOrder(newOrder());
        OutboxEventRelayProperties properties = properties(500, Duration.ofSeconds(1));
        properties.setMaxAttempts(1);
        OutboxEventRelay failingRelay = new OutboxEventRelay(
                tx,
                outboxEventRepository,
                event -> {
                    throw new RuntimeException("Poison event");
                },
                properties
        );

        OutboxEventRelay.PollResult firstPoll = failingRelay.pollBatch(10);
        OutboxEventRelay.PollResult secondPoll = failingRelay.pollBatch(10);

        assertThat(firstPoll).isEqualTo(new OutboxEventRelay.PollResult(1, 0, 1));
        assertThat(secondPoll).isEqualTo(new OutboxEventRelay.PollResult(0, 0, 0));
        assertThat(jdbc.sql("""
                        SELECT status, attempt_count, last_error
                        FROM outbox_events
                        """)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "status", rs.getString("status"),
                        "attempt_count", rs.getInt("attempt_count"),
                        "last_error", rs.getString("last_error")
                ))
                .single())
                .containsEntry("status", "FINAL_FAILED")
                .containsEntry("attempt_count", 1)
                .containsEntry("last_error", "Poison event");
    }

    @Test
    void outboxRelay_commitsClaimBeforeConsumerRuns() {
        service.createOrder(newOrder());
        OutboxEventRelay relay = new OutboxEventRelay(
                tx,
                outboxEventRepository,
                event -> assertThat(jdbc.sql("""
                                SELECT status
                                FROM outbox_events
                                WHERE event_id = :eventId
                                """)
                        .param("eventId", event.getEventId())
                        .query(String.class)
                        .single())
                        .isEqualTo("PROCESSING"),
                properties(500, Duration.ofSeconds(1))
        );

        OutboxEventRelay.PollResult result = relay.pollBatch(10);

        assertThat(result).isEqualTo(new OutboxEventRelay.PollResult(1, 1, 0));
    }

    @Test
    void outboxRelay_reclaimsExpiredProcessingEvent() {
        service.createOrder(newOrder());
        jdbc.sql("""
                        UPDATE outbox_events
                        SET status = 'PROCESSING',
                            claimed_at = now() - interval '1 minute',
                            claim_until = now() - interval '1 second',
                            attempt_count = 1
                        """)
                .update();

        OutboxEventRelay.PollResult result = outboxEventRelay.pollBatch(10);

        assertThat(result).isEqualTo(new OutboxEventRelay.PollResult(1, 1, 0));
        Map<String, Object> row = jdbc.sql("""
                        SELECT status, attempt_count
                        FROM outbox_events
                        """)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "status", rs.getString("status"),
                        "attempt_count", rs.getInt("attempt_count")
                ))
                .single();
        assertThat(row)
                .containsEntry("status", "PROCESSED")
                .containsEntry("attempt_count", 2);
    }

    @Test
    void outboxRepository_staleClaim_doesNotOverwriteReclaimedEvent() {
        service.createOrder(newOrder());
        OutboxEvent firstClaim = outboxEventRepository.claimBatch(1, Duration.ofMinutes(1)).getFirst();
        jdbc.sql("""
                        UPDATE outbox_events
                        SET claim_until = now() - interval '1 second'
                        WHERE event_id = :eventId
                        """)
                .param("eventId", firstClaim.getEventId())
                .update();
        OutboxEvent secondClaim = outboxEventRepository.claimBatch(1, Duration.ofMinutes(2)).getFirst();

        boolean updated = outboxEventRepository.markProcessed(firstClaim.getEventId(), firstClaim.getClaimUntil());

        assertThat(updated).isFalse();
        assertThat(secondClaim.getClaimUntil()).isAfter(firstClaim.getClaimUntil());
        assertThat(jdbc.sql("""
                        SELECT status, claim_until
                        FROM outbox_events
                        WHERE event_id = :eventId
                        """)
                .param("eventId", firstClaim.getEventId())
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "status", rs.getString("status"),
                        "claim_until", rs.getObject("claim_until", OffsetDateTime.class)
                ))
                .single())
                .containsEntry("status", "PROCESSING")
                .containsEntry("claim_until", secondClaim.getClaimUntil());
    }

    @Test
    void outboxSchema_rejectsProcessingWithoutClaimUntil() {
        service.createOrder(newOrder());

        assertThatThrownBy(() -> jdbc.sql("""
                        UPDATE outbox_events
                        SET status = 'PROCESSING',
                            claimed_at = now(),
                            claim_until = NULL
                        """)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxSchema_rejectsProcessingWithoutClaimedAt() {
        service.createOrder(newOrder());

        assertThatThrownBy(() -> jdbc.sql("""
                        UPDATE outbox_events
                        SET status = 'PROCESSING',
                            claimed_at = NULL,
                            claim_until = now() + interval '30 seconds'
                        """)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxSchema_rejectsProcessedWithoutProcessedAt() {
        service.createOrder(newOrder());

        assertThatThrownBy(() -> jdbc.sql("""
                        UPDATE outbox_events
                        SET status = 'PROCESSED',
                            processed_at = NULL
                        """)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxSchema_rejectsProcessedWithClaimFields() {
        service.createOrder(newOrder());

        assertThatThrownBy(() -> jdbc.sql("""
                        UPDATE outbox_events
                        SET status = 'PROCESSED',
                            processed_at = now(),
                            claimed_at = now(),
                            claim_until = now() + interval '30 seconds'
                        """)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createOrder_rollsBackOrderAndOutboxEvent_whenFailureAfterOutboxInsert() {
        RuntimeException error = new RuntimeException("Simulated failure after outbox insert");
        OrderService failingService = new OrderService(
                tx,
                orderRepository,
                failingAfterSaveOutboxRepository(error),
                payloadSerializer
        );

        assertThatThrownBy(() -> failingService.createOrder(newOrder()))
                .isSameAs(error);
        assertThat(countRows("orders")).isZero();
        assertThat(countRows("outbox_events")).isZero();
    }

    private void seedUser(int userId) {
        jdbc.sql("""
                        INSERT INTO users(user_id, username, email)
                        VALUES (:userId, :username, :email)
                        """)
                .param("userId", userId)
                .param("username", "user-" + userId)
                .param("email", "user-" + userId + "@example.com")
                .update();
    }

    private int countRows(String tableName) {
        return jdbc.sql("SELECT COUNT(*) FROM " + tableName)
                .query(Integer.class)
                .single();
    }

    private int unprocessedEventCount() {
        return jdbc.sql("SELECT COUNT(*) FROM outbox_events WHERE processed_at IS NULL")
                .query(Integer.class)
                .single();
    }

    private OutboxEventRepository failingAfterSaveOutboxRepository(RuntimeException error) {
        return new OutboxEventRepository() {
            @Override
            public void save(OutboxEvent event) {
                outboxEventRepository.save(event);
                throw error;
            }

            @Override
            public List<OutboxEvent> claimBatch(int batchSize, Duration processingTimeout) {
                return outboxEventRepository.claimBatch(batchSize, processingTimeout);
            }

            @Override
            public boolean markProcessed(UUID eventId, OffsetDateTime claimUntil) {
                return outboxEventRepository.markProcessed(eventId, claimUntil);
            }

            @Override
            public boolean markFailed(
                    UUID eventId,
                    OffsetDateTime claimUntil,
                    String lastError,
                    Duration nextAttemptDelay,
                    int maxAttempts
            ) {
                return outboxEventRepository.markFailed(eventId, claimUntil, lastError, nextAttemptDelay, maxAttempts);
            }
        };
    }

    private static Order newOrder() {
        Order order = new Order();
        order.setUserId(7);
        order.setProductName("Keyboard");
        order.setAmount(new BigDecimal("99.90"));
        return order;
    }

    private static OutboxEventRelayProperties properties(int maxErrorLength, Duration nextAttemptDelay) {
        OutboxEventRelayProperties properties = new OutboxEventRelayProperties();
        properties.setMaxErrorLength(maxErrorLength);
        properties.setNextAttemptDelay(nextAttemptDelay);
        properties.setProcessingTimeout(Duration.ofSeconds(30));
        properties.setMaxAttempts(3);
        return properties;
    }

    private record OutboxEventRow(String aggregateType, String aggregateId, String eventType) {
    }

    private record ProcessedRow(
            String status,
            boolean processed,
            OffsetDateTime claimedAt,
            OffsetDateTime claimUntil
    ) {
    }
}
