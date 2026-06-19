package ru.gold.ordance.repository.examples.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.gold.ordance.jdbc.examples.common.Asserts;

public class JacksonOutboxEventPayloadSerializer implements OutboxEventPayloadSerializer {

    private final ObjectMapper objectMapper;

    public JacksonOutboxEventPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Asserts.nonNull(objectMapper, "objectMapper");
    }

    @Override
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload.", e);
        }
    }
}
