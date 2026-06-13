package ru.gold.ordance.repository.examples.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.jdbc.examples.common.Asserts;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;

import java.util.Map;

public class OrderRepositoryImpl implements OrderRepository {

    private static final String CREATE_ORDER = """
            INSERT INTO orders(user_id, product_name, amount)
            VALUES (:userId, :productName, :amount)
            RETURNING order_id, user_id, product_name, amount, created_at
            """;

    private final JdbcClient jdbc;

    public OrderRepositoryImpl(JdbcClient jdbc) {
        this.jdbc = Asserts.nonNull(jdbc, "jdbc");
    }

    @Override
    public Order save(Order order) {
        Map<String, Object> params = Map.of(
                "userId", order.getUserId(),
                "productName", order.getProductName(),
                "amount", order.getAmount()
        );
        return jdbc.sql(CREATE_ORDER)
                .params(params)
                .query((rs, rowNum) -> {
                    Order savedOrder = new Order();
                    savedOrder.setOrderId(rs.getInt("order_id"));
                    savedOrder.setUserId(rs.getInt("user_id"));
                    savedOrder.setProductName(rs.getString("product_name"));
                    savedOrder.setAmount(rs.getBigDecimal("amount"));
                    savedOrder.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return savedOrder;
                })
                .single();
    }
}
