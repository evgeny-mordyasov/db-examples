package ru.gold.ordance.repository.examples.outbox.service;

public interface OutboxEventPayloadSerializer {

    String serialize(Object payload);
}
