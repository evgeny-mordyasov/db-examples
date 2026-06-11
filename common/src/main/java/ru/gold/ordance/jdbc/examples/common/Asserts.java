package ru.gold.ordance.jdbc.examples.common;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;

@SuppressWarnings("UnusedReturnValue")
public final class Asserts {

    private Asserts() {
    }

    @Nonnull
    public static <T> T nonNull(@Nullable T object, @Nonnull String paramName) {
        if (object == null) {
            throw new IllegalArgumentException("[" + paramName + "] must not be null.");
        }
        return object;
    }

    @Nonnull
    public static String nonEmpty(@Nullable String string, @Nonnull String paramName) {
        if (string == null || string.isEmpty()) {
            throw new IllegalArgumentException("[" + paramName + "] must not be null or empty.");
        }
        return string;
    }

    @Nonnull
    public static <K, V> Map<K, V> nonEmpty(@Nullable  Map<K, V> map, @Nonnull String paramName) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("[" + paramName + "] must not be null or empty.");
        }
        return map;
    }

    public static int positive(int val, @Nonnull String paramName) {
        if (val <= 0) {
            throw new IllegalArgumentException("[" + paramName + "] must be a positive number");
        }
        return val;
    }

    public static long positive(long val, @Nonnull String paramName) {
        if (val <= 0) {
            throw new IllegalArgumentException("[" + paramName + "] must be a positive number");
        }
        return val;
    }
}
