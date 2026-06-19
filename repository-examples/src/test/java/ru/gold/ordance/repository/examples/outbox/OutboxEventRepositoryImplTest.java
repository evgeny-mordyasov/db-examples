package ru.gold.ordance.repository.examples.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.model.OutboxEvent;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepositoryImpl;

import java.sql.ResultSet;
import java.time.Duration;
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
    @Mock private JdbcClient.StatementSpec statementSpec;
    @Mock private JdbcClient.MappedQuerySpec<OutboxEvent> querySpec;

    @Test
    void createInstance_jdbcIsNull() {
        assertThatThrownBy(() -> new OutboxEventRepositoryImpl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[jdbc] must not be null.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_success() {
        OutboxEvent event = event();
        String payload = """
                {"orderId":11,"userId":7,"productName":"Keyboard","amount":99.90,"createdAt":"2026-01-02T03:04:00+03:00"}
                """;
        event.setPayload(payload);
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.params(anyMap())).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        repository.save(event);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).sql(sqlCaptor.capture());
        verify(statementSpec).params(paramsCaptor.capture());
        verify(statementSpec).update();

        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO outbox_events")
                .contains("CAST(:payload AS jsonb)");
        assertThat(paramsCaptor.getValue())
                .containsEntry("eventId", event.getEventId())
                .containsEntry("aggregateType", "Order")
                .containsEntry("aggregateId", "11")
                .containsEntry("eventType", "OrderCreated")
                .containsEntry("payload", payload);
    }

    @Test
    void claimBatch_processingTimeoutIsZero() {
        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        assertThatThrownBy(() -> repository.claimBatch(5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[processingTimeout] must be a positive duration");
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimBatch_success() throws Exception {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OffsetDateTime claimUntil = OffsetDateTime.parse("2026-01-02T03:05:00+03:00");
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getObject("event_id", UUID.class)).thenReturn(eventId);
        when(rs.getString("aggregate_type")).thenReturn("Order");
        when(rs.getString("aggregate_id")).thenReturn("11");
        when(rs.getString("event_type")).thenReturn("OrderCreated");
        when(rs.getString("payload")).thenReturn("{\"orderId\":11}");
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
        when(rs.getObject("processed_at", OffsetDateTime.class)).thenReturn(null);
        when(rs.getString("status")).thenReturn("PROCESSING");
        when(rs.getObject("claimed_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
        when(rs.getObject("claim_until", OffsetDateTime.class)).thenReturn(claimUntil);
        when(rs.getInt("attempt_count")).thenReturn(1);
        when(rs.getString("last_error")).thenReturn(null);
        when(rs.getObject("next_attempt_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-01-02T03:06:00+03:00"));
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("batchSize", 5)).thenReturn(statementSpec);
        when(statementSpec.param("processingTimeoutMillis", 30_000L)).thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.list()).thenAnswer(invocation -> {
            ArgumentCaptor<RowMapper<OutboxEvent>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
            verify(statementSpec).query(rowMapperCaptor.capture());
            return List.of(rowMapperCaptor.getValue().mapRow(rs, 0));
        });

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        List<OutboxEvent> events = repository.claimBatch(5, Duration.ofSeconds(30));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("UPDATE outbox_events")
                .contains("RETURNING")
                .contains("status = 'PROCESSING'")
                .contains("attempt_count = attempt_count + 1")
                .contains("claim_until = now() + (:processingTimeoutMillis * interval '1 millisecond')")
                .contains("event.status")
                .contains("event.claimed_at")
                .contains("event.claim_until")
                .contains("processed_at IS NULL")
                .contains("status IN ('PENDING', 'FAILED') AND next_attempt_at <= now()")
                .contains("status = 'PROCESSING' AND claim_until <= now()")
                .contains("ORDER BY created_at, event_id")
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
                    assertThat(event.getStatus()).isEqualTo("PROCESSING");
                    assertThat(event.getClaimedAt()).isEqualTo(OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
                    assertThat(event.getClaimUntil()).isEqualTo(claimUntil);
                    assertThat(event.getAttemptCount()).isEqualTo(1);
                    assertThat(event.getLastError()).isNull();
                    assertThat(event.getNextAttemptAt()).isEqualTo(OffsetDateTime.parse("2026-01-02T03:06:00+03:00"));
                });
    }

    @Test
    void markProcessed_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OffsetDateTime claimUntil = OffsetDateTime.parse("2026-01-02T03:05:00+03:00");
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("eventId", eventId)).thenReturn(statementSpec);
        when(statementSpec.param("claimUntil", claimUntil)).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        boolean updated = repository.markProcessed(eventId, claimUntil);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(updated).isTrue();
        assertThat(sqlCaptor.getValue())
                .contains("status = 'PROCESSED'")
                .contains("processed_at = now()")
                .contains("last_error = NULL")
                .contains("claimed_at = NULL")
                .contains("claim_until = NULL")
                .contains("WHERE event_id = :eventId")
                .contains("AND status = 'PROCESSING'")
                .contains("AND claim_until = :claimUntil");
    }

    @Test
    void markProcessed_staleClaim_returnFalse() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OffsetDateTime claimUntil = OffsetDateTime.parse("2026-01-02T03:05:00+03:00");
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("eventId", eventId)).thenReturn(statementSpec);
        when(statementSpec.param("claimUntil", claimUntil)).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(0);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        assertThat(repository.markProcessed(eventId, claimUntil)).isFalse();
    }

    @Test
    void markFailed_success() {
        UUID eventId = UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a");
        OffsetDateTime claimUntil = OffsetDateTime.parse("2026-01-02T03:05:00+03:00");
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param("eventId", eventId)).thenReturn(statementSpec);
        when(statementSpec.param("claimUntil", claimUntil)).thenReturn(statementSpec);
        when(statementSpec.param("lastError", "Timeout")).thenReturn(statementSpec);
        when(statementSpec.param("nextAttemptDelayMillis", 60_000L)).thenReturn(statementSpec);
        when(statementSpec.param("maxAttempts", 3)).thenReturn(statementSpec);
        when(statementSpec.update()).thenReturn(1);

        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        boolean updated = repository.markFailed(eventId, claimUntil, "Timeout", Duration.ofMinutes(1), 3);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).sql(sqlCaptor.capture());
        assertThat(updated).isTrue();
        assertThat(sqlCaptor.getValue())
                .contains("WHEN attempt_count >= :maxAttempts THEN 'FINAL_FAILED'")
                .contains("ELSE 'FAILED'")
                .doesNotContain("attempt_count = attempt_count + 1")
                .contains("last_error = :lastError")
                .contains("next_attempt_at = now() + (:nextAttemptDelayMillis * attempt_count * interval '1 millisecond')")
                .contains("claimed_at = NULL")
                .contains("claim_until = NULL")
                .contains("WHERE event_id = :eventId")
                .contains("AND status = 'PROCESSING'")
                .contains("AND claim_until = :claimUntil");
    }

    @Test
    void markFailed_nextAttemptDelayIsZero() {
        OutboxEventRepository repository = new OutboxEventRepositoryImpl(jdbc);

        assertThatThrownBy(() -> repository.markFailed(
                        UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a"),
                        OffsetDateTime.parse("2026-01-02T03:05:00+03:00"),
                        "Timeout",
                        Duration.ZERO,
                        3
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[nextAttemptDelay] must be a positive duration");
    }

    private static OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.fromString("7a2517f2-e651-4569-9f96-ac09f8b64f9a"));
        event.setAggregateType("Order");
        event.setAggregateId("11");
        event.setEventType("OrderCreated");
        return event;
    }
}
