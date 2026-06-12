package ru.gold.ordance.jdbc.examples.common.exception;

public class TooManyUsersFoundException extends RuntimeException {

    public TooManyUsersFoundException(String message) {
        super(message);
    }
}
