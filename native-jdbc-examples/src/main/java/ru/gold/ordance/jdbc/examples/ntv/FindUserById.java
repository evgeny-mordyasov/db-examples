package ru.gold.ordance.jdbc.examples.ntv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_USERNAME;

@SuppressWarnings("Duplicates")
public class FindUserById {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindUserById.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            WHERE user_id = ?
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();

    public static void main(String[] args) throws Exception {
        int userId = 1;
        Optional<User> user = findByUserId(userId);
        if (user.isPresent()) {
            LOGGER.info("Found user by userId = {}: \n\t{}", userId, user.get());
        } else {
            LOGGER.info("No user found by userId = {}", userId);
        }
    }

    private static Optional<User> findByUserId(int userId) throws SQLException {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            try (PreparedStatement ps = con.prepareStatement(QUERY)) {
                ps.setInt(1, userId);

                try (ResultSet rs = ps.executeQuery()) {
                    List<User> users = new ArrayList<>();
                    while (rs.next()) {
                        users.add(MAPPER.map(rs));
                    }
                    if (users.size() > 1) {
                        throw new SQLException("Too many users found: " + users.size());
                    }
                    return users.isEmpty()
                            ? Optional.empty()
                            : Optional.of(users.getFirst());
                }
            }
        }
    }
}
