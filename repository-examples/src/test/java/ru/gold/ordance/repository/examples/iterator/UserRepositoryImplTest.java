package ru.gold.ordance.repository.examples.iterator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.model.User;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserRepositoryImplTest {

    @Mock private JdbcClient jdbc;
    @Mock private JdbcClient.StatementSpec statementSpec;
    @Mock private JdbcClient.MappedQuerySpec<User> querySpec;

    @Test
    void createInstance_jdbcIsNull() {
        assertThatThrownBy(() -> new UserRepositoryImpl(null, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[jdbc] must not be null.");
    }

    @Test
    void createInstance_batchSizeIsZero() {
        assertThatThrownBy(() -> new UserRepositoryImpl(jdbc, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[batchSize] must be a positive number");
    }

    @Test
    void createInstance_batchSizeIsNegative() {
        assertThatThrownBy(() -> new UserRepositoryImpl(jdbc, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[batchSize] must be a positive number");
    }

    @Test
    @SuppressWarnings("unchecked")
    void iterator_empty_ShouldReturnNoUsers() {
        stubUserPages(List.of());

        Iterator<User> iterator = new UserRepositoryImpl(jdbc, 2).iterator();

        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next)
                .isInstanceOf(NoSuchElementException.class);

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(statementSpec, times(2)).params(paramsCaptor.capture());
        assertThat(paramsCaptor.getAllValues())
                .allSatisfy(params -> assertThat(params)
                        .containsEntry("batchSize", 2)
                        .doesNotContainKey("userId"));
    }

    @Test
    void iterator_sqlFailed_ShouldThrowException() {
        RuntimeException error = new RuntimeException("Simulated error");
        when(jdbc.sql(anyString())).thenThrow(error);

        Iterator<User> iterator = new UserRepositoryImpl(jdbc, 2).iterator();

        assertThatThrownBy(iterator::hasNext)
                .isSameAs(error);
    }

    @Test
    @SuppressWarnings("unchecked")
    void iterator_success_ShouldLoadUsersLazily() {
        stubUserPages(
                List.of(user(1), user(2)),
                List.of(user(3)),
                List.of()
        );

        Iterator<User> iterator = new UserRepositoryImpl(jdbc, 2).iterator();

        verifyNoInteractions(jdbc);
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next().getUserId()).isOne();
        assertThat(iterator.next().getUserId()).isEqualTo(2);
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next().getUserId()).isEqualTo(3);
        assertThat(iterator.hasNext()).isFalse();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbc, times(3)).sql(sqlCaptor.capture());
        verify(statementSpec, times(3)).params(paramsCaptor.capture());

        assertThat(sqlCaptor.getAllValues().get(0)).doesNotContain("WHERE user_id > :userId");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("WHERE user_id > :userId");
        assertThat(sqlCaptor.getAllValues().get(2)).contains("WHERE user_id > :userId");
        assertThat(paramsCaptor.getAllValues().get(0)).containsEntry("batchSize", 2);
        assertThat(paramsCaptor.getAllValues().get(1))
                .containsEntry("batchSize", 2)
                .containsEntry("userId", 2);
        assertThat(paramsCaptor.getAllValues().get(2))
                .containsEntry("batchSize", 2)
                .containsEntry("userId", 3);
    }

    @SafeVarargs
    private void stubUserPages(List<User>... pages) {
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.params(anyMap())).thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.list()).thenReturn(pages[0], copyOfTail(pages));
    }

    private static List<User>[] copyOfTail(List<User>[] pages) {
        return java.util.Arrays.copyOfRange(pages, 1, pages.length);
    }

    private static User user(int id) {
        User user = new User();
        user.setUserId(id);
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@mail.test");
        user.setCreatedAt(LocalDateTime.of(2026, 1, id, 0, 0));
        return user;
    }
}
