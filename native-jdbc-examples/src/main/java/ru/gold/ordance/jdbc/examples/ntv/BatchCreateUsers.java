package ru.gold.ordance.jdbc.examples.ntv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_USERNAME;
import static ru.gold.ordance.jdbc.examples.common.db.generator.UserGenerator.generateUsers;

@SuppressWarnings("Duplicates")
public class BatchCreateUsers {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchCreateUsers.class);

    private static final int BATCH_SIZE = 10;

    private static final String QUERY = """
            INSERT INTO users(username, email)
            VALUES (?, ?)
            """;

    public static void main(String[] args) throws SQLException {
        List<User> users = generateUsers(BATCH_SIZE);
        createUsers(users);
    }

    private static void createUsers(List<User> users) throws SQLException {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(QUERY)) {
                for (User user : users) {
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getEmail());
                    ps.addBatch();
                }

                int[] res = ps.executeBatch();
                con.commit();
                LOGGER.info("{} new users were inserted successfully.", res.length);
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }
}
