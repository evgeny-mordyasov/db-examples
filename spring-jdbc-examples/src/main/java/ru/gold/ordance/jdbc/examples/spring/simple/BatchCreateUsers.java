package ru.gold.ordance.jdbc.examples.spring.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
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
            VALUES (?, ?)
            """;

    private static final DataSource dataSource = createDataSource();
    private static final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    private static final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

    public static void main(String[] args) {
        List<User> users = generateUsers(USERS_COUNT);
        createUsers(users);
    }

    private static void createUsers(List<User> users) {
        tx.executeWithoutResult(ignored -> {
            ParameterizedPreparedStatementSetter<User> setter = (ps, user) -> {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getEmail());
            };

            jdbc.batchUpdate(QUERY, users, users.size(), setter);
            LOGGER.info("Batch insert completed: inserted users = {}.", users.size());
        });
    }
}
