package ru.gold.ordance.repository.examples.outbox.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.repository.examples.outbox.service.JacksonOutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.service.LoggingOutboxEventConsumer;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.service.OrderService;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventConsumer;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventRelay;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;

@Configuration(proxyBeanMethods = false)
public class ServiceConfig {

    @Bean
    @ConfigurationProperties("outbox.relay")
    OutboxEventRelayProperties outboxEventRelayProperties() {
        return new OutboxEventRelayProperties();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    OutboxEventPayloadSerializer outboxEventPayloadSerializer(ObjectMapper objectMapper) {
        return new JacksonOutboxEventPayloadSerializer(objectMapper);
    }

    @Bean
    OutboxEventConsumer outboxEventConsumer() {
        return new LoggingOutboxEventConsumer();
    }

    @Bean
    OutboxEventRelay outboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository outboxEventRepository,
            OutboxEventConsumer outboxEventConsumer,
            OutboxEventRelayProperties properties
    ) {
        return new OutboxEventRelay(
                transactionTemplate,
                outboxEventRepository,
                outboxEventConsumer,
                properties
        );
    }

    @Bean
    OrderService orderService(
            TransactionTemplate transactionTemplate,
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            OutboxEventPayloadSerializer payloadSerializer
    ) {
        return new OrderService(transactionTemplate, orderRepository, outboxEventRepository, payloadSerializer);
    }
}
