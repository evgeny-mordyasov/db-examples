package ru.gold.ordance.repository.examples.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryImplTest {

    @Mock private JdbcClient jdbc;
    @Mock private OutboxEventPayloadSerializer payloadSerializer;
    @Mock private JdbcClient.StatementSpec statementSpec;

    @Test
    void createInstance_jdbcIsNull() {
        assertThatThrownBy(() -> new OutboxEventRepositoryImpl(null, payloadSerializer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[jdbc] must not be null.");
    }

    @Test
    void createInstance_payloadSerializerIsNull() {
        assertThatThrownBy(() -> new OutboxEventRepositoryImpl(jdbc, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[payloadSerializer] must not be null.");
    }

    @Test
    void save_serializeFailed() {
        Order order = order();
        RuntimeException error = new RuntimeException("Simulated error");
        when(payloadSerializer.serialize(any(Order.class))).thenThrow(error);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc, payloadSerializer);

        assertThatThrownBy(() -> repository.save(order))
                .isSameAs(error);
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_success() {
        Order order = order();
        String payload = """
                {"orderId":11,"userId":7,"productName":"Keyboard","amount":99.90,"createdAt":"2026-01-02T03:04:00+03:00"}
                """;
        when(payloadSerializer.serialize(any(Order.class))).thenReturn(payload);
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.params(anyMap())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc, payloadSerializer);

        repository.save(order);

        ArgumentCaptor<Order> payloadCaptor = ArgumentCaptor.forClass(Order.class);
        verify(payloadSerializer).serialize(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .extracting(
                        Order::getOrderId,
                        Order::getUserId,
                        Order::getProductName,
                        Order::getAmount,
                        Order::getCreatedAt
                )
                .containsExactly(11, 7, "Keyboard", new BigDecimal("99.90"), OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).sql(sqlCaptor.capture());
        verify(statementSpec).params(paramsCaptor.capture());
        verify(statementSpec).update();

        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO outbox_events")
                .contains("CAST(:payload AS jsonb)");
        assertThat(paramsCaptor.getValue())
                .containsEntry("aggregateType", "Order")
                .containsEntry("aggregateId", "11")
                .containsEntry("eventType", "OrderCreated")
                .containsEntry("payload", payload);
        assertThat(paramsCaptor.getValue().get("eventId")).isInstanceOf(UUID.class);
    }

    private static Order order() {
        Order order = new Order();
        order.setOrderId(11);
        order.setUserId(7);
        order.setProductName("Keyboard");
        order.setAmount(new BigDecimal("99.90"));
        order.setCreatedAt(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
        return order;
    }
}
