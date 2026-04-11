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

import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_PASSWORD;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_URL;
import static ru.gold.ordance.jdbc.examples.common.db.DbProps.DB_USERNAME;

@SuppressWarnings("Duplicates")
public class FindAllUsersByLimitOffset {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsersByLimitOffset.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            ORDER BY user_id
            LIMIT ? OFFSET ?
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();

    public static void main(String[] args) throws Exception {
        int page = 1;
        int pageSize = 10;

        while (true) {
            int offset = pageSize * (page - 1);
            List<User> users = findUsersBy(pageSize, offset);
            if (users.isEmpty()) break;
            StringBuilder sb = new StringBuilder();
            users.forEach(user -> sb.append("\t").append(user.toString()).append("\n"));
            LOGGER.info("Found {} users (page={}): \n{}", users.size(), page++, sb);
        }
    }

    private static List<User> findUsersBy(int pageSize, int offset) throws SQLException {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            try (PreparedStatement ps = con.prepareStatement(QUERY)) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);

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
