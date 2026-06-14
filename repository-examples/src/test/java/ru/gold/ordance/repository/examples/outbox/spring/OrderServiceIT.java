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
import ru.gold.ordance.jdbc.examples.testcontainers.TestcontainersPostgresConfiguration;
import ru.gold.ordance.repository.examples.outbox.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.OrderService;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersPostgresConfiguration.class)
class OrderServiceIT {

    @Autowired private JdbcClient jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
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
    }

    @Test
    void createOrder_rollsBackOrderAndOutboxEvent_whenFailureAfterOutboxInsert() {
        RuntimeException error = new RuntimeException("Simulated failure after outbox insert");
        OrderService failingService = new OrderService(
                tx,
                orderRepository,
                order -> {
                    outboxEventRepository.save(order);
                    throw error;
                }
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

    private static Order newOrder() {
        Order order = new Order();
        order.setUserId(7);
        order.setProductName("Keyboard");
        order.setAmount(new BigDecimal("99.90"));
        return order;
    }

    private record OutboxEventRow(String aggregateType, String aggregateId, String eventType) {
    }
}
