package ru.gold.ordance.jdbc.examples.common.db.generator;

import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class UserGenerator {

    private UserGenerator() {
    }

    public static User generateUser() {
        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setEmail(UUID.randomUUID() + "@gmail.com");
        return user;
    }

    public static List<User> generateUsers(int count) {
        return IntStream.range(0, count)
                .mapToObj(ignored -> generateUser())
                .toList();
    }
}
