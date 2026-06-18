package ru.gold.ordance.repository.examples.outbox.spring;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.testcontainers.Containers;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.service.OrderService;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventRelay;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:00Z"), ZoneOffset.UTC);
        OutboxEventRelay failingRelay = new OutboxEventRelay(
                tx,
                outboxEventRepository,
                event -> {
                    throw new RuntimeException("Consumer failed");
                },
                properties(500, Duration.ofSeconds(1)),
                clock
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
                .containsEntry("last_error", "Consumer failed")
                .containsEntry("next_attempt_at", OffsetDateTime.parse("2026-01-02T03:04:01Z"));
        assertThat(row.get("processed_at")).isNull();
        assertThat(row.get("claimed_at")).isNull();
        assertThat(row.get("claim_until")).isNull();
    }

    @Test
    void outboxRelay_marksFinalFailedAfterMaxAttemptsAndSkipsRetry() {
        service.createOrder(newOrder());
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:00Z"), ZoneOffset.UTC);
        OutboxEventRelayProperties properties = properties(500, Duration.ofSeconds(1));
        properties.setMaxAttempts(1);
        OutboxEventRelay failingRelay = new OutboxEventRelay(
                tx,
                outboxEventRepository,
                event -> {
                    throw new RuntimeException("Poison event");
                },
                properties,
                clock
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
        Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:00Z"), ZoneOffset.UTC);
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
                properties(500, Duration.ofSeconds(1)),
                clock
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
        OffsetDateTime oldClaimUntil = OffsetDateTime.parse("2026-01-02T03:05:00Z");
        OffsetDateTime newClaimUntil = OffsetDateTime.parse("2030-01-02T03:05:00Z");
        OutboxEvent firstClaim = outboxEventRepository.claimBatch(1, oldClaimUntil).getFirst();
        OutboxEvent secondClaim = outboxEventRepository.claimBatch(1, newClaimUntil).getFirst();

        boolean updated = outboxEventRepository.markProcessed(firstClaim.getEventId(), firstClaim.getClaimUntil());

        assertThat(updated).isFalse();
        assertThat(firstClaim.getClaimUntil()).isEqualTo(oldClaimUntil);
        assertThat(secondClaim.getClaimUntil()).isEqualTo(newClaimUntil);
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
                .containsEntry("claim_until", newClaimUntil);
    }

    @Test
    void createOrder_rollsBackOrderAndOutboxEvent_whenFailureAfterOutboxInsert() {
        RuntimeException error = new RuntimeException("Simulated failure after outbox insert");
        OrderService failingService = new OrderService(
                tx,
                orderRepository,
                failingAfterSaveOutboxRepository(error)
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
            public void save(Order order) {
                outboxEventRepository.save(order);
                throw error;
            }

            @Override
            public List<OutboxEvent> claimBatch(int batchSize, OffsetDateTime claimUntil) {
                return outboxEventRepository.claimBatch(batchSize, claimUntil);
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
                    OffsetDateTime nextAttemptAt,
                    int maxAttempts
            ) {
                return outboxEventRepository.markFailed(eventId, claimUntil, lastError, nextAttemptAt, maxAttempts);
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
