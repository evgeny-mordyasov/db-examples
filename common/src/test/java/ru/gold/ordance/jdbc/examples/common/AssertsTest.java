package ru.gold.ordance.jdbc.examples.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("ConstantConditions")
class AssertsTest {

    @Test
    void nonNull_withNullValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.nonNull(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[value] must not be null.");
    }

    @Test
    void nonNull_withValidValue_ShouldReturnValue() {
        Object value = new Object();

        Object result = Asserts.nonNull(value, "value");

        assertThat(result).isSameAs(value);
    }

    @Test
    void nonEmptyString_withNullValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.nonEmpty((String) null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[value] must not be null or empty.");
    }

    @Test
    void nonEmptyString_withEmptyValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.nonEmpty("", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[value] must not be null or empty.");
    }

    @Test
    void nonEmptyString_withValidValue_ShouldReturnValue() {
        String result = Asserts.nonEmpty("data", "value");

        assertThat(result).isEqualTo("data");
    }

    @Test
    void nonEmptyMap_withNullValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.nonEmpty((Map<String, Object>) null, "map"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[map] must not be null or empty.");
    }

    @Test
    void nonEmptyMap_withEmptyValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.nonEmpty(Map.of(), "map"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[map] must not be null or empty.");
    }

    @Test
    void nonEmptyMap_withValidValue_ShouldReturnValue() {
        Map<String, Object> map = Map.of("key", "value");

        Map<String, Object> result = Asserts.nonEmpty(map, "map");

        assertThat(result).isSameAs(map);
    }

    @Test
    void positive_int_withZeroValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.positive(0, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[count] must be a positive number");
    }

    @Test
    void positive_int_withNegativeValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.positive(-1, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[count] must be a positive number");
    }

    @Test
    void positive_long_withZeroValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.positive(0L, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[count] must be a positive number");
    }

    @Test
    void positive_long_withNegativeValue_ShouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> Asserts.positive(-1L, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[count] must be a positive number");
    }

    @Test
    void positive_withPositiveValue_ShouldReturnValue() {
        long result = Asserts.positive(1, "count");

        assertThat(result).isOne();
    }
}
