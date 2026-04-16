package ru.gold.ordance.jdbc.examples.spring.named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import ru.gold.ordance.jdbc.examples.common.db.generator.UserGenerator;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.Map;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class CreateUser {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateUser.class);

    private static final String QUERY = """
            INSERT INTO users(username, email)
            VALUES (:username, :email)
            """;

    private static final NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(new JdbcTemplate(createDataSource()));

    public static void main(String[] args) {
        User user = UserGenerator.generateUser();
        createUser(user);
    }

    private static void createUser(User user) {
        Map<String, Object> params = Map.of("username", user.getUsername(), "email", user.getEmail());
        int res = jdbc.update(QUERY, params);
        if (res == 0) {
            throw new RuntimeException("Failed to insert new user.");
        }
        LOGGER.info("A new user was inserted successfully.");
    }
}
