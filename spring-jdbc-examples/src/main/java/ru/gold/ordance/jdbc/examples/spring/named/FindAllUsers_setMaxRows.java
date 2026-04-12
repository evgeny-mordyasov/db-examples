package ru.gold.ordance.jdbc.examples.spring.named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class FindAllUsers_setMaxRows {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsers_setMaxRows.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            """;

    private static final int MAX_ROWS = 10;
    private static final RowMapper<User> MAPPER = new UserRowMapper();
    private static final NamedParameterJdbcTemplate jdbc;

    static {
        JdbcTemplate template = new JdbcTemplate(createDataSource());
        template.setMaxRows(MAX_ROWS);
        jdbc = new NamedParameterJdbcTemplate(template);
    }

    public static void main(String[] args) {
        List<User> users = findAllUser();
        StringBuilder sb = new StringBuilder();
        users.forEach(user -> sb.append("\t").append(user.toString()).append("\n"));
        LOGGER.info("Found {} users: \n{}", users.size(), sb);
    }

    private static List<User> findAllUser() {
        return jdbc.query(QUERY, (rs, num) -> MAPPER.map(rs));
    }
}
