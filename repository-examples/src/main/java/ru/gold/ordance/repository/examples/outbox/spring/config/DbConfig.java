package ru.gold.ordance.repository.examples.outbox.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.repository.examples.outbox.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.OrderRepositoryImpl;
import ru.gold.ordance.repository.examples.outbox.OutboxEventPayloadSerializer;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.OutboxEventRepositoryImpl;

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
