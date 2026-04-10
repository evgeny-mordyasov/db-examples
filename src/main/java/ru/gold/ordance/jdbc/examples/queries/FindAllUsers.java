package ru.gold.ordance.jdbc.examples.queries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gold.ordance.jdbc.examples.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static ru.gold.ordance.jdbc.examples.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.DbProps.DB_USERNAME;

public class FindAllUsers {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsers.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            """;

    public static void main(String[] args) throws Exception {
        List<User> users = findAllUser();
        StringBuilder sb = new StringBuilder();
        users.forEach(user -> sb.append("\t").append(user.toString()).append("\n"));
        LOGGER.info("Found {} users: \n{}", users.size(), sb);
    }

    private static List<User> findAllUser() throws SQLException {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            try (Statement s = con.createStatement()) {
                try (ResultSet rs = s.executeQuery(QUERY)) {
                    List<User> users = new ArrayList<>();
                    while (rs.next()) {
                        User user = new User();
                        user.setUserId(rs.getInt("user_id"));
                        user.setUsername(rs.getString("username"));
                        user.setEmail(rs.getString("email"));
                        user.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                        users.add(user);
                    }
                    return users;
                }
            }
        }
    }
}
