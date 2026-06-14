package ru.gold.ordance.repository.examples.outbox.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepositoryImpl;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepositoryImpl;

@Configuration
public class DbConfig {

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
}
