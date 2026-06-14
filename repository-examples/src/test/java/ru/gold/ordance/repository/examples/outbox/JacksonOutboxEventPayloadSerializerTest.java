package ru.gold.ordance.repository.examples.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JacksonOutboxEventPayloadSerializerTest {

    @Test
    void createInstance_objectMapperIsNull() {
        assertThatThrownBy(() -> new JacksonOutboxEventPayloadSerializer(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[objectMapper] must not be null.");
    }

    @Test
    void serialize_success() {
        OutboxEventPayloadSerializer serializer = new JacksonOutboxEventPayloadSerializer(objectMapper());

        String payload = serializer.serialize(order());

        assertThat(payload)
                .isEqualTo("{\"orderId\":11,\"userId\":7,\"productName\":\"Keyboard\",\"amount\":99.90,\"createdAt\":\"2026-01-02T03:04:00+03:00\"}");
    }

    @Test
    void serialize_failed() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonProcessingException error = new JsonProcessingException("Simulated error") {
        };
        when(objectMapper.writeValueAsString(any(Order.class))).thenThrow(error);
        OutboxEventPayloadSerializer serializer = new JacksonOutboxEventPayloadSerializer(objectMapper);

        assertThatThrownBy(() -> serializer.serialize(order()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize outbox event payload.")
                .hasCause(error);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
