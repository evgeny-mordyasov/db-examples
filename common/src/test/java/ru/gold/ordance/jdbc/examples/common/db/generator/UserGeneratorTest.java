package ru.gold.ordance.jdbc.examples.common.db.generator;

import org.junit.jupiter.api.Test;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserGeneratorTest {

    @Test
    void generateUsers_withCount_ShouldReturnGeneratedUsers() {
        List<User> users = UserGenerator.generateUsers(3);

        assertThat(users)
                .hasSize(3)
                .allSatisfy(user -> {
                    assertThat(user.getUsername()).isNotBlank();
                    assertThat(user.getEmail()).endsWith("@gmail.com");
                });
    }
}
