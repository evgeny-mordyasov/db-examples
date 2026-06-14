package ru.gold.ordance.repository.examples.outbox.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.repository.examples.outbox.JacksonOutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.LoggingOutboxEventConsumer;
import ru.gold.ordance.repository.examples.outbox.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.OrderRepositoryImpl;
import ru.gold.ordance.repository.examples.outbox.OrderService;
import ru.gold.ordance.repository.examples.outbox.OutboxEventConsumer;
import ru.gold.ordance.repository.examples.outbox.OutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRelay;
import ru.gold.ordance.repository.examples.outbox.properties.OutboxEventRelayProperties;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepositoryImpl;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {

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
    OrderRepository orderRepository(JdbcClient jdbcClient) {
        return new OrderRepositoryImpl(jdbcClient);
    }

    @Bean
    OutboxEventRepository outboxEventRepository(
            JdbcClient jdbcClient,
            OutboxEventPayloadSerializer payloadSerializer
    ) {
        return new OutboxEventRepositoryImpl(jdbcClient, payloadSerializer);
    }

    @Bean
    OutboxEventConsumer outboxEventConsumer() {
        return new LoggingOutboxEventConsumer();
    }

    @Bean
    @ConfigurationProperties("outbox.relay")
    OutboxEventRelayProperties outboxEventRelayProperties() {
        return new OutboxEventRelayProperties();
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    OutboxEventRelay outboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository outboxEventRepository,
            OutboxEventConsumer outboxEventConsumer,
            OutboxEventRelayProperties properties,
            Clock clock
    ) {
        return new OutboxEventRelay(
                transactionTemplate,
                outboxEventRepository,
                outboxEventConsumer,
                properties,
                clock
        );
    }

    @Bean
    OrderService orderService(
            TransactionTemplate transactionTemplate,
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        return new OrderService(transactionTemplate, orderRepository, outboxEventRepository);
    }
}
