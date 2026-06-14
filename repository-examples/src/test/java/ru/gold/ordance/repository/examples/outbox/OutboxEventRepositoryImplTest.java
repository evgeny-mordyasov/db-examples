package ru.gold.ordance.repository.examples.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;

import java.sql.ResultSet;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
    @Mock private JdbcClient.MappedQuerySpec<OutboxEvent> querySpec;

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

    @Test
    @SuppressWarnings("unchecked")
    void findUnprocessedBatch_success() throws Exception {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getObject("event_id", UUID.class)).thenReturn(eventId);
        when(rs.getString("aggregate_type")).thenReturn("Order");
        when(rs.getString("aggregate_id")).thenReturn("11");
        when(rs.getString("event_type")).thenReturn("OrderCreated");
        when(rs.getString("payload")).thenReturn("{\"orderId\":11}");
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
        when(rs.getObject("processed_at", OffsetDateTime.class)).thenReturn(null);
        when(rs.getInt("attempt_count")).thenReturn(1);
        when(rs.getString("last_error")).thenReturn("Timeout");
        when(rs.getObject("next_attempt_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-01-02T03:06:00+03:00"));

        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("batchSize", 5)).thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.list()).thenAnswer(invocation -> {
            ArgumentCaptor<RowMapper<OutboxEvent>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
            verify(statementSpec).query(rowMapperCaptor.capture());
            return List.of(rowMapperCaptor.getValue().mapRow(rs, 0));
        });

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc, payloadSerializer);

        List<OutboxEvent> events = repository.findUnprocessedBatch(5);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("processed_at IS NULL")
                .contains("next_attempt_at <= now()")
                .contains("ORDER BY created_at")
                .contains("LIMIT :batchSize")
                .contains("FOR UPDATE SKIP LOCKED");
        assertThat(events).singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventId()).isEqualTo(eventId);
                    assertThat(event.getAggregateType()).isEqualTo("Order");
                    assertThat(event.getAggregateId()).isEqualTo("11");
                    assertThat(event.getEventType()).isEqualTo("OrderCreated");
                    assertThat(event.getPayload()).isEqualTo("{\"orderId\":11}");
                    assertThat(event.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
                    assertThat(event.getProcessedAt()).isNull();
                    assertThat(event.getAttemptCount()).isEqualTo(1);
                    assertThat(event.getLastError()).isEqualTo("Timeout");
                    assertThat(event.getNextAttemptAt()).isEqualTo(OffsetDateTime.parse("2026-01-02T03:06:00+03:00"));
                });
    }

    @Test
    void markProcessed_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("eventId", eventId)).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc, payloadSerializer);

        repository.markProcessed(eventId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("processed_at = now()")
                .contains("last_error = NULL")
                .contains("WHERE event_id = :eventId");
    }

    @Test
    void markFailed_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OffsetDateTime nextAttemptAt = OffsetDateTime.parse("2026-01-02T03:06:00+03:00");
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("eventId", eventId)).thenReturn(statementSpec);
        when(statementSpec.param("lastError", "Timeout")).thenReturn(statementSpec);
        when(statementSpec.param("nextAttemptAt", nextAttemptAt)).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc, payloadSerializer);

        repository.markFailed(eventId, "Timeout", nextAttemptAt);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("attempt_count = attempt_count + 1")
                .contains("last_error = :lastError")
                .contains("next_attempt_at = :nextAttemptAt")
                .contains("WHERE event_id = :eventId");
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
