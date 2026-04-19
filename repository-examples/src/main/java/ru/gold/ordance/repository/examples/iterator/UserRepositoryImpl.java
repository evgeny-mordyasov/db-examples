package ru.gold.ordance.repository.examples.iterator;

import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.RowMapper;
import ru.gold.ordance.jdbc.examples.common.db.UserRowMapper;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class UserRepositoryImpl implements UserRepository {

    private static final RowMapper<User> MAPPER = new UserRowMapper();

    private final JdbcClient jdbc;
    private final int batchSize;

    public UserRepositoryImpl(JdbcClient jdbc, int batchSize) {
        this.jdbc = jdbc;
        this.batchSize = batchSize;
    }

    private static final String GET_FIRST_BATCH = """
            SELECT user_id, username, email, created_at
            FROM users
            ORDER BY user_id
            LIMIT :batchSize
            """;

    private static final String GET_NEXT_BATCH = """
            SELECT user_id, username, email, created_at
            FROM users
            WHERE user_id > :userId
            ORDER BY user_id
            LIMIT :batchSize
            """;

    @Override
    public Iterator<User> iterator() {
        return new UserRepositoryIterator() {
            @Override
            List<User> getUsers(Integer userId) {
                Map<String, Object> params = new HashMap<>(Map.of("batchSize", batchSize));
                if (userId == null) {
                    return jdbc.sql(GET_FIRST_BATCH).params(params).query((rs, num) -> MAPPER.map(rs)).list();
                } else {
                    params.put("userId", userId);
                    return jdbc.sql(GET_NEXT_BATCH).params(params).query((rs, num) -> MAPPER.map(rs)).list();
                }
            }
        };
    }

    private abstract static class UserRepositoryIterator implements Iterator<User> {

        private Iterator<User> pageIt;
        private Integer lastUserId;

        abstract List<User> getUsers(Integer userId);

        @Override
        public User next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            User user = pageIt.next();
            lastUserId = user.getUserId();
            return user;
        }

        @Override
        public boolean hasNext() {
            if (pageIt == null || !pageIt.hasNext()) {
                List<User> users = getUsers(lastUserId);
                if (!users.isEmpty()) {
                    pageIt = users.iterator();
                }
            }
            if (pageIt != null) {
                return pageIt.hasNext();
            }
            return false;
        }
    }
}
