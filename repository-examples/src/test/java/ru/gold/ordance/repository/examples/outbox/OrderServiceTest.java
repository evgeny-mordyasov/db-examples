package ru.gold.ordance.repository.examples.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.gold.ordance.jdbc.examples.common.db.model.Order;
import ru.gold.ordance.repository.examples.outbox.repository.OrderRepository;
import ru.gold.ordance.repository.examples.outbox.repository.OutboxEventRepository;
import ru.gold.ordance.repository.examples.outbox.service.OrderService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private TransactionTemplate tx;

    @Test
    void createInstance_txIsNull() {
        assertThatThrownBy(() -> new OrderService(null, orderRepository, outboxEventRepository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[tx] must not be null.");
    }

    @Test
    void createInstance_orderRepositoryIsNull() {
        assertThatThrownBy(() -> new OrderService(tx, null, outboxEventRepository))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[orderRepository] must not be null.");
    }

    @Test
    void createInstance_outboxEventRepositoryIsNull() {
        assertThatThrownBy(() -> new OrderService(tx, orderRepository, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("[outboxEventRepository] must not be null.");
    }

    @Test
    void createOrder_orderRepositoryFailed() {
        Order newOrder = newOrder();
        RuntimeException error = new RuntimeException("Simulated error");
        executeTransactionCallback();
        when(orderRepository.save(newOrder)).thenThrow(error);

        OrderService service = new OrderService(tx, orderRepository, outboxEventRepository);

        assertThatThrownBy(() -> service.createOrder(newOrder))
                .isSameAs(error);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void createOrder_outboxEventRepositoryFailed() {
        Order newOrder = newOrder();
        Order savedOrder = savedOrder();
        RuntimeException error = new RuntimeException("Simulated error");
        executeTransactionCallback();
        when(orderRepository.save(newOrder)).thenReturn(savedOrder);
        doThrow(error).when(outboxEventRepository).save(savedOrder);

        OrderService service = new OrderService(tx, orderRepository, outboxEventRepository);

        assertThatThrownBy(() -> service.createOrder(newOrder))
                .isSameAs(error);
    }

    @Test
    void createOrder_success() {
        Order newOrder = newOrder();
        Order savedOrder = savedOrder();
        executeTransactionCallback();
        when(orderRepository.save(newOrder)).thenReturn(savedOrder);

        OrderService service = new OrderService(tx, orderRepository, outboxEventRepository);

        Order result = service.createOrder(newOrder);

        assertThat(result).isSameAs(savedOrder);
        verify(orderRepository).save(newOrder);
        verify(outboxEventRepository).save(savedOrder);
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallback() {
        when(tx.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Order> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private static Order newOrder() {
        return order(0, 7, "Keyboard", new BigDecimal("99.90"), null);
    }

    private static Order savedOrder() {
        return order(11, 7, "Keyboard", new BigDecimal("99.90"), OffsetDateTime.parse("2026-01-02T03:04:00+03:00"));
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
