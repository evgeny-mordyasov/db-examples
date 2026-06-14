package ru.gold.ordance.repository.examples.outbox.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.repository.examples.outbox.service.OrderService;
import ru.gold.ordance.repository.examples.outbox.service.OutboxEventRelay;

import java.math.BigDecimal;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Main.class);
        application.setAdditionalProfiles("outbox");
        application.run(args);
    }

    @Bean
    @Profile("!test")
    ApplicationRunner outboxExampleRunner(OrderService orderService, OutboxEventRelay outboxEventRelay) {
        return args -> {
            Order newOrder = new Order();
            newOrder.setUserId(1);
            newOrder.setProductName("Keyboard");
            newOrder.setAmount(new BigDecimal("99.90"));
            Order savedOrder = orderService.createOrder(newOrder);
            LOGGER.info("Created order and outbox event in one transaction: {}", savedOrder);
            OutboxEventRelay.PollResult result = outboxEventRelay.pollBatch(10);
            LOGGER.info("Outbox relay poll result: {}", result);
        };
    }
}
