package ru.gold.ordance.repository.examples.iterator;

import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.util.Iterator;

public interface UserRepository {

    Iterator<User> iterator();
}
