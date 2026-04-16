package ru.gold.ordance.jdbc.examples.spring.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;
import java.util.Optional;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class FindUserById {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindUserById.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            WHERE user_id = ?
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();
    private static final JdbcTemplate jdbc = new JdbcTemplate(createDataSource());

    public static void main(String[] args) {
        int userId = 1;
        Optional<User> user = findByUserId(userId);
        if (user.isPresent()) {
            LOGGER.info("Found user by userId = {}: \n\t{}", userId, user.get());
        } else {
            LOGGER.info("No user found by userId = {}", userId);
        }
    }

    private static Optional<User> findByUserId(int userId) {
        List<User> users = jdbc.query(QUERY, (rs, num) -> MAPPER.map(rs), userId);
        if (users.size() > 1) {
            throw new RuntimeException("Too many users found: " + users.size());
        }
        return users.isEmpty()
                ? Optional.empty()
                : Optional.of(users.getFirst());
    }
}
