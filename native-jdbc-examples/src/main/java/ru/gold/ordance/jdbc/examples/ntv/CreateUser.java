package ru.gold.ordance.jdbc.examples.ntv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gold.ordance.jdbc.examples.common.db.generator.UserGenerator;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_USERNAME;

@SuppressWarnings("Duplicates")
public class CreateUser {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateUser.class);

    private static final String QUERY = """
            INSERT INTO users(username, email)
            VALUES (?, ?)
            """;

    public static void main(String[] args) throws SQLException {
        User user = UserGenerator.generateUser();
        createUser(user);
    }

    private static void createUser(User user) throws SQLException {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            try (PreparedStatement ps = con.prepareStatement(QUERY)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getEmail());

                int res = ps.executeUpdate();
                if (res == 0) {
                    throw new SQLException("Failed to insert new user.");
                }
                LOGGER.info("A new user was inserted successfully.");
            }
        }
    }
}
