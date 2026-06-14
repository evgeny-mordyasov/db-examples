package ru.gold.ordance.jdbc.examples.common.db;

import org.junit.jupiter.api.Test;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserRowMapperTest {

    @Test
    void map_withTimestampWithTimeZoneColumn_ShouldReadCreatedAt() throws SQLException {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        ResultSet resultSet = resultSet(createdAt);

        User user = new UserRowMapper().map(resultSet);

        assertThat(user.getUserId()).isEqualTo(7);
        assertThat(user.getUsername()).isEqualTo("maria");
        assertThat(user.getEmail()).isEqualTo("maria@gmail.com");
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    }

    private static ResultSet resultSet(LocalDateTime createdAt) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInt" -> 7;
                    case "getString" -> switch ((String) args[0]) {
                        case "username" -> "maria";
                        case "email" -> "maria@gmail.com";
                        default -> null;
                    };
                    case "getTimestamp" -> Timestamp.valueOf(createdAt);
                    case "getObject" -> throw new SQLException("Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime.");
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
