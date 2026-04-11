package ru.gold.ordance.jdbc.examples.spring.fluent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;
import java.util.Map;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class FindAllUsersByLimitOffset {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsersByLimitOffset.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            ORDER BY user_id
            LIMIT :pageSize OFFSET :offset
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();
    private static final JdbcClient jdbc = JdbcClient.create(createDataSource());

    public static void main(String[] args) {
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

    private static List<User> findUsersBy(int pageSize, int offset) {
        Map<String, Object> params = Map.of("pageSize", pageSize, "offset", offset);
        return jdbc.sql(QUERY).params(params).query((rs, num) -> MAPPER.map(rs)).list();
    }
}
