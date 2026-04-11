package ru.gold.ordance.jdbc.examples.spring.named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class FindAllUsersByCursor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsersByCursor.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            WHERE user_id > :lastUserId
            ORDER BY user_id
            LIMIT :pageSize
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();
    private static final NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(new JdbcTemplate(createDataSource()));

    public static void main(String[] args) {
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

    private static List<User> findUsersBy(int pageSize, Integer lastUserId) {
        Map<String, Object> params = Map.of("pageSize", pageSize, "lastUserId", Objects.requireNonNullElse(lastUserId, 0));
        return jdbc.query(QUERY, params, (rs, num) -> MAPPER.map(rs));
    }
}
