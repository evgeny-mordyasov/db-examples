package ru.gold.ordance.repository.examples.iterator.spring;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import ru.gold.ordance.jdbc.examples.common.db.model.User;
import ru.gold.ordance.jdbc.examples.testcontainers.Containers;
import ru.gold.ordance.repository.examples.iterator.UserRepository;
import ru.gold.ordance.repository.examples.iterator.UserRepositoryImpl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(Containers.class)
class UserRepositoryIT {

    @Autowired private JdbcClient jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private Flyway flyway;

    @BeforeEach
    void setUp() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void iterator_emptyTable_ShouldReturnNoUsers() {
        Iterator<User> iterator = userRepository.iterator();

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next)
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void iterator_multipleBatches_ShouldReturnUsersInOrder() {
        seedUsers(5);
        UserRepository repository = new UserRepositoryImpl(jdbc, 2);

        List<User> users = readAll(repository);

        assertThat(users)
                .extracting(User::getUserId)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(users)
                .extracting(User::getUsername)
                .containsExactly("user-1", "user-2", "user-3", "user-4", "user-5");
    }

    @Test
    void iterator_exactBatchBoundary_ShouldReturnUsersInOrder() {
        seedUsers(4);
        UserRepository repository = new UserRepositoryImpl(jdbc, 2);

        List<User> users = readAll(repository);

        assertThat(users)
                .extracting(User::getUserId)
                .containsExactly(1, 2, 3, 4);
    }

    private List<User> readAll(UserRepository repository) {
        Iterator<User> iterator = repository.iterator();
        ArrayList<User> users = new ArrayList<>();
        iterator.forEachRemaining(users::add);
        return users;
    }

    private void seedUsers(int count) {
        for (int id = 1; id <= count; id++) {
            seedUser(id);
        }
    }

    private void seedUser(int userId) {
        jdbc.sql("""
                        INSERT INTO users(user_id, username, email)
                        VALUES (:userId, :username, :email)
                        """)
                .param("userId", userId)
                .param("username", "user-" + userId)
                .param("email", "user-" + userId + "@example.com")
                .update();
    }
}
