package ru.gold.ordance.repository.examples.outbox.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
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
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepositoryImpl;

import java.time.Duration;

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
    OutboxEventRelay outboxEventRelay(
            TransactionTemplate transactionTemplate,
            OutboxEventRepository outboxEventRepository,
            OutboxEventConsumer outboxEventConsumer,
            @Value("${outbox.relay.max-error-length}") int maxErrorLength,
            @Value("${outbox.relay.next-attempt-delay}") Duration nextAttemptDelay
    ) {
        return new OutboxEventRelay(
                transactionTemplate,
                outboxEventRepository,
                outboxEventConsumer,
                new OutboxEventRelay.Properties(maxErrorLength, nextAttemptDelay)
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
