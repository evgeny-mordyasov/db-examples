package ru.gold.ordance.jdbc.examples.spring.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;
import java.util.Objects;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class FindAllUsersByImprovedCursor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsersByImprovedCursor.class);

    private static final String QUERY_BY_OFFSET = """
            SELECT user_id, username, email, created_at
            FROM users
            ORDER BY user_id
            LIMIT ? OFFSET ?
            """;

    private static final String QUERY_BY_CURSOR = """
            SELECT user_id, username, email, created_at
            FROM users
            WHERE user_id > ?
            ORDER BY user_id
            LIMIT ?
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();
    private static final JdbcTemplate jdbc = new JdbcTemplate(createDataSource());

    public static void main(String[] args) {
        Integer lastUserId = null;
        int page = 1;
        int pageSize = 10;

        while (true) {
            List<User> users;
            if (lastUserId == null) {
                int offset = pageSize * (page - 1);
                users = findUsersBy(pageSize, offset);
            } else {
                users = findUsersBy(pageSize, lastUserId);
            }

            if (users.isEmpty()) break;
            StringBuilder sb = new StringBuilder();
            users.forEach(user -> sb.append("\t").append(user.toString()).append("\n"));
            LOGGER.info("Found {} users: \n{}", users.size(), sb);
            lastUserId = users.getLast().getUserId();
        }
    }

    private static List<User> findUsersBy(int pageSize, int offset) {
        return jdbc.query(QUERY_BY_OFFSET, (rs, num) -> MAPPER.map(rs), pageSize, offset);

    }

    private static List<User> findUsersBy(int pageSize, Integer lastUserId) {
        return jdbc.query(QUERY_BY_CURSOR, (rs, num) -> MAPPER.map(rs), Objects.requireNonNullElse(lastUserId, 0), pageSize);
    }
}
