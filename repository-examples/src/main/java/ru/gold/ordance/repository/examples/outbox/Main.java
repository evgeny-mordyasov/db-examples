package ru.gold.ordance.repository.examples.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import javax.sql.DataSource;
import java.math.BigDecimal;

import static ru.gold.ordance.repository.examples.DbUtils.createDataSource;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        DataSource dataSource = createDataSource();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        OrderService service = new OrderService(
                tx,
                new OrderRepositoryImpl(jdbc),
                new OutboxEventRepositoryImpl(jdbc, new JacksonOutboxEventPayloadSerializer(objectMapper))
        );

        Order newOrder = new Order();
        newOrder.setUserId(1);
        newOrder.setProductName("Keyboard");
        newOrder.setAmount(new BigDecimal("99.90"));
        Order savedOrder = service.createOrder(newOrder);
        LOGGER.info("Created order and outbox event in one transaction: {}", savedOrder);
    }
}
