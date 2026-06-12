package ru.gold.ordance.jdbc.examples.spring.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import javax.sql.DataSource;
import java.util.List;

import static ru.gold.ordance.jdbc.examples.spring.DbUtils.createDataSource;

@SuppressWarnings("Duplicates")
public class FindAllUsers_setTransactionIsolation {

    private static final Logger LOGGER = LoggerFactory.getLogger(FindAllUsers_setTransactionIsolation.class);

    private static final String QUERY = """
            SELECT user_id, username, email, created_at
            FROM users
            """;

    private static final RowMapper<User> MAPPER = new UserRowMapper();
    private static final DataSource dataSource = createDataSource();
    private static final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    private static final TransactionTemplate tx;

    static {
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public static void main(String[] args) {
        List<User> users = findAllUser();
        StringBuilder sb = new StringBuilder();
        users.forEach(user -> sb.append("\t").append(user.toString()).append("\n"));
        LOGGER.info("Found {} users: \n{}", users.size(), sb);
    }

    private static List<User> findAllUser() {
        return tx.execute(status -> jdbc.query(QUERY, (rs, num) -> MAPPER.map(rs)));
    }
}
