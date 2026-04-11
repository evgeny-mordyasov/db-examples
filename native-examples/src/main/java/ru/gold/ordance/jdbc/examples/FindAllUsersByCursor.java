package ru.gold.ordance.jdbc.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gold.ordance.jdbc.examples.db.RowMapper;
import ru.gold.ordance.jdbc.examples.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.db.model.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static ru.gold.ordance.jdbc.examples.db.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.db.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.db.DbProps.DB_USERNAME;

@SuppressWarnings("Duplicates")
public class FindAllUsersByCursor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsersByCursor.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            WHERE user_id > ?
            ORDER BY user_id
            LIMIT ?
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();

    public static void main(String[] args) throws Exception {
        Integer lastUserId = null;
        int pageSize = 10;

        while (true) {
            List<User> users = findUsersBy(pageSize, lastUserId);
            if (users.isEmpty()) break;
            StringBuilder sb = new StringBuilder();
            users.forEach(user -> sb.append("\t").append(user.toString()).append("\n"));
            LOGGER.info("Found {} users: \n{}", users.size(), sb);
            lastUserId = users.getLast().getUserId();
        }
    }

    private static List<User> findUsersBy(int pageSize, Integer lastUserId) throws SQLException {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            try (PreparedStatement ps = con.prepareStatement(QUERY)) {
                ps.setInt(1, Objects.requireNonNullElse(lastUserId, 0));
                ps.setInt(2, pageSize);

                try (ResultSet rs = ps.executeQuery()) {
                    List<User> users = new ArrayList<>();
                    while (rs.next()) {
                        users.add(MAPPER.map(rs));
                    }
                    return users;
                }
            }
        }
    }
}
