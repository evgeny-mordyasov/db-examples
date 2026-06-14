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
import java.util.List;
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
                        SELECT processed_at IS NOT NULL
                        FROM outbox_events
                        """)
                .query(Boolean.class)
                .single())
                .isTrue();
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
        RetryRow row = jdbc.sql("""
                        SELECT processed_at,
                               attempt_count,
                               last_error,
                               next_attempt_at
                        FROM outbox_events
                        """)
                .query((rs, rowNum) -> new RetryRow(
                        rs.getObject("processed_at", OffsetDateTime.class),
                        rs.getInt("attempt_count"),
                        rs.getString("last_error"),
                        rs.getObject("next_attempt_at", OffsetDateTime.class)
                ))
                .single();
        assertThat(row.processedAt()).isNull();
        assertThat(row.attemptCount()).isEqualTo(1);
        assertThat(row.lastError()).isEqualTo("Consumer failed");
        assertThat(row.nextAttemptAt()).isEqualTo(OffsetDateTime.parse("2026-01-02T03:04:01Z"));
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
            public List<OutboxEvent> findUnprocessedBatch(int batchSize) {
                return outboxEventRepository.findUnprocessedBatch(batchSize);
            }

            @Override
            public void markProcessed(UUID eventId) {
                outboxEventRepository.markProcessed(eventId);
            }

            @Override
            public void markFailed(UUID eventId, String lastError, OffsetDateTime nextAttemptAt) {
                outboxEventRepository.markFailed(eventId, lastError, nextAttemptAt);
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
        return properties;
    }

    private record OutboxEventRow(String aggregateType, String aggregateId, String eventType) {
    }

    private record RetryRow(
            OffsetDateTime processedAt,
            int attemptCount,
            String lastError,
            OffsetDateTime nextAttemptAt
    ) {
    }
}
