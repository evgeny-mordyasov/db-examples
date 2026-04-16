package ru.gold.ordance.jdbc.examples.common.db.generator;

import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.UUID;

public final class UserGenerator {

    private UserGenerator() {
    }

    public static User generateUser() {
        User user = new User();
        user.setUsername(UUID.randomUUID().toString());
        user.setEmail(UUID.randomUUID() + "@gmail.com");
        return user;
    }
}
