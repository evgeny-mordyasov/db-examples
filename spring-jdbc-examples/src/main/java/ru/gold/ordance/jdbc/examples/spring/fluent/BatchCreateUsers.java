package ru.gold.ordance.jdbc.examples.spring.fluent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import javax.sql.DataSource;
import java.util.List;

import static ru.gold.ordance.jdbc.examples.common.db.generator.UserGenerator.generateUsers;
import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class BatchCreateUsers {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchCreateUsers.class);

    private static final int USERS_COUNT = 10;

    private static final String QUERY = """
            INSERT INTO users(username, email)
            VALUES (:username, :email)
            """;

    private static final DataSource dataSource = createDataSource();
    private static final NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(new JdbcTemplate(dataSource));
    private static final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

    public static void main(String[] args) {
        List<User> users = generateUsers(USERS_COUNT);
        createUsers(users);
    }

    private static void createUsers(List<User> users) {
        tx.executeWithoutResult(ignored -> {
            SqlParameterSource[] params = users.stream()
                    .map(user -> new MapSqlParameterSource()
                            .addValue("username", user.getUsername())
                            .addValue("email", user.getEmail()))
                    .toArray(SqlParameterSource[]::new);

            jdbc.batchUpdate(QUERY, params);
            LOGGER.info("Batch insert completed: inserted users = {}.", users.size());
        });
    }
}
