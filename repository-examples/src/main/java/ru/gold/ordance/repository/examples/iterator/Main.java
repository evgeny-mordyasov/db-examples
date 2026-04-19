package ru.gold.ordance.repository.examples.iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import static ru.gold.ordance.repository.examples.DbUtils.createDataSource;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        JdbcClient jdbc = JdbcClient.create(createDataSource());
        int batchSize = 10;
        UserRepository repository = new UserRepositoryImpl(jdbc, batchSize);

        repository.iterator().forEachRemaining(user -> LOGGER.info("Found user: {}", user));
    }
}
