package ru.gold.ordance.jdbc.examples.spring.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.gold.ordance.jdbc.examples.common.db.generator.UserGenerator;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class CreateUser {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateUser.class);

    private static final String QUERY = """
            INSERT INTO users(username, email)
            VALUES (?, ?)
            """;

    private static final JdbcTemplate jdbc = new JdbcTemplate(createDataSource());

    public static void main(String[] args) {
        User user = UserGenerator.generateUser();
        createUser(user);
    }

    private static void createUser(User user) {
        int res = jdbc.update(QUERY, user.getUsername(), user.getEmail());
        if (res == 0) {
            throw new RuntimeException("Failed to insert new user.");
        }
        LOGGER.info("A new user was inserted successfully.");
    }
}
