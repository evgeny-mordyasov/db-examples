package ru.gold.ordance.repository.examples.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderRepositoryImplTest {

    @Mock private JdbcClient jdbc;
    @Mock private JdbcClient.StatementSpec statementSpec;
    @Mock private JdbcClient.MappedQuerySpec<Order> querySpec;
    @Mock private ResultSet resultSet;

    @Test
    void createInstance_jdbcIsNull() {
        assertThatThrownBy(() -> new OrderRepositoryImpl(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[jdbc] must not be null.");
    }

    @Test
    void create_success() throws Exception {
        Order newOrder = order(0, 7, "Keyboard", new BigDecimal("99.90"), null);
        Order order = order(11, 7, "Keyboard", new BigDecimal("99.90"), null);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-02T03:04:00+03:00");
        when(jdbc.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.params(anyMap())).thenReturn(statementSpec);
        when(statementSpec.query(any(RowMapper.class))).thenReturn(querySpec);
        when(querySpec.single()).thenReturn(order);

        Order result = new OrderRepositoryImpl(jdbc).save(newOrder);

        assertThat(result).isSameAs(order);

        ArgumentCaptor<RowMapper<Order>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        verify(statementSpec).query(mapperCaptor.capture());
        when(resultSet.getInt("order_id")).thenReturn(11);
        when(resultSet.getInt("user_id")).thenReturn(7);
        when(resultSet.getString("product_name")).thenReturn("Keyboard");
        when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("99.90"));
        when(resultSet.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);
        assertThat(mapperCaptor.getValue().mapRow(resultSet, 0))
                .extracting(
                        Order::getOrderId,
                        Order::getUserId,
                        Order::getProductName,
                        Order::getAmount,
                        Order::getCreatedAt
                )
                .containsExactly(11, 7, "Keyboard", new BigDecimal("99.90"), createdAt);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jdbc).sql(sqlCaptor.capture());
        verify(statementSpec).params(paramsCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO orders(user_id, product_name, amount)")
                .contains("RETURNING order_id, user_id, product_name, amount, created_at");
        assertThat(paramsCaptor.getValue())
                .containsEntry("userId", 7)
                .containsEntry("productName", "Keyboard")
                .containsEntry("amount", new BigDecimal("99.90"));
    }

    private static Order order(int orderId, int userId, String productName, BigDecimal amount, OffsetDateTime createdAt) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setProductName(productName);
        order.setAmount(amount);
        order.setCreatedAt(createdAt);
        return order;
    }
}
