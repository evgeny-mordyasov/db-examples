package ru.gold.ordance.jdbc.examples.spring.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static ru.gold.ordance.jdbc.examples.common.db.generator.UserGenerator.generateUsers;
import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class BatchCreateUsers {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchCreateUsers.class);

    private static final int BATCH_SIZE = 10;

    private static final String QUERY = """
            INSERT INTO users(username, email)
            VALUES (?, ?)
            """;

    private static final DataSource dataSource = createDataSource();
    private static final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    private static final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

    public static void main(String[] args) {
        List<User> users = generateUsers(BATCH_SIZE);
        createUsers(users);
    }

    private static void createUsers(List<User> users) {
        tx.executeWithoutResult(ignored -> {
            int[] res = jdbc.batchUpdate(QUERY, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    User user = users.get(i);
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getEmail());
                }

                @Override
                public int getBatchSize() {
                    return users.size();
                }
            });
            LOGGER.info("{} new users were inserted successfully.", res.length);
        });
    }
}
