package com.nexusmarket.domain;

import com.nexusmarket.domain.enums.CartStatus;
import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartOrderTest {

    @Test
    void shouldCalculateCartTotal() {
        Cart cart = new Cart(UUID.randomUUID(), UUID.randomUUID());

        cart.addItem(UUID.randomUUID(), "Teclado",
                new BigDecimal("100000"), 2);
        cart.addItem(UUID.randomUUID(), "Mouse",
                new BigDecimal("50000"), 1);

        assertEquals(new BigDecimal("250000"), cart.getTotal());
        assertEquals(2, cart.getItems().size());
    }

    @Test
    void shouldIncreaseQuantityForRepeatedProduct() {
        Cart cart = new Cart(UUID.randomUUID(), UUID.randomUUID());
        UUID productId = UUID.randomUUID();

        cart.addItem(productId, "Teclado",
                new BigDecimal("100000"), 1);
        cart.addItem(productId, "Teclado",
                new BigDecimal("100000"), 2);

        assertEquals(1, cart.getItems().size());
        assertEquals(3, cart.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("300000"), cart.getTotal());
    }

    @Test
    void shouldExposeCartItemsThroughUnmodifiableList() {
        Cart cart = new Cart(UUID.randomUUID(), UUID.randomUUID());

        cart.addItem(
                UUID.randomUUID(),
                "Teclado",
                new BigDecimal("100000"),
                2
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> cart.getItems().clear()
        );

        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getQuantity());
    }

    @Test
    void shouldRejectQuantityOverflowWithoutChangingCart() {
        Cart cart = new Cart(UUID.randomUUID(), UUID.randomUUID());
        UUID productId = UUID.randomUUID();

        cart.addItem(
                productId,
                "Teclado",
                new BigDecimal("100000"),
                Integer.MAX_VALUE
        );

        assertThrows(
                ArithmeticException.class,
                () -> cart.addItem(
                        productId,
                        "Teclado",
                        new BigDecimal("100000"),
                        1
                )
        );

        assertEquals(
                Integer.MAX_VALUE,
                cart.getItems().get(0).getQuantity()
        );
    }

    @Test
    void shouldPreserveQuantityWhenAddingItemsConcurrently() {
        Cart cart = new Cart(UUID.randomUUID(), UUID.randomUUID());
        UUID productId = UUID.randomUUID();

        java.util.stream.IntStream.range(0, 100)
                .parallel()
                .forEach(i -> cart.addItem(
                        productId,
                        "Teclado",
                        new BigDecimal("100000"),
                        1
                ));

        assertEquals(1, cart.getItems().size());
        assertEquals(100, cart.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("10000000"), cart.getTotal());
    }

    @Test
    void shouldBlockChangesAfterCartConversion() {
        Cart cart = new Cart(UUID.randomUUID(), UUID.randomUUID());
        cart.addItem(UUID.randomUUID(), "Mouse",
                new BigDecimal("50000"), 1);

        cart.convert();

        assertEquals(CartStatus.CONVERTED, cart.getStatus());
        assertThrows(IllegalStateException.class,
                () -> cart.addItem(UUID.randomUUID(), "Teclado",
                        new BigDecimal("100000"), 1));
    }

    @Test
    void shouldRejectEmptyOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> new Order(UUID.randomUUID(),
                        UUID.randomUUID(), List.of()));
    }

    @Test
    void shouldFollowOrderLifecycle() {
        OrderItem item = new OrderItem(
                UUID.randomUUID(), "Teclado",
                new BigDecimal("100000"), 2);

        Order order = new Order(
                UUID.randomUUID(), UUID.randomUUID(), List.of(item));

        assertEquals(new BigDecimal("200000"), order.getTotal());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());

        order.registerPayment(true);
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());

        order.dispatch();
        assertEquals(OrderStatus.DISPATCHED, order.getStatus());

        order.markDelivered();
        assertEquals(OrderStatus.DELIVERED, order.getStatus());

        order.finalizeOrder();
        assertEquals(OrderStatus.FINALIZED, order.getStatus());

        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void shouldRestoreOrderStateWhenPersistenceFails() {
        OrderItem item = new OrderItem(
                UUID.randomUUID(), "Teclado",
                new BigDecimal("100000"), 1);

        Order order = new Order(
                UUID.randomUUID(), UUID.randomUUID(), List.of(item));

        assertThrows(IllegalStateException.class,
                () -> order.transitionAndPersist(
                        () -> order.registerPayment(true),
                        () -> {
                            throw new IllegalStateException(
                                    "Fallo simulado de persistencia.");
                        }));

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void shouldKeepFinalizedOrderImmutableWhenTransitionIsAttempted() {
        OrderItem item = new OrderItem(
                UUID.randomUUID(), "Teclado",
                new BigDecimal("100000"), 1);

        Order order = new Order(
                UUID.randomUUID(), UUID.randomUUID(), List.of(item));

        order.registerPayment(true);
        order.dispatch();
        order.markDelivered();
        order.finalizeOrder();

        assertThrows(IllegalStateException.class,
                () -> order.transitionAndPersist(
                        order::cancel, () -> {}));

        assertEquals(OrderStatus.FINALIZED, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }

    @Test
    void shouldNotDispatchBeforePayment() {
        OrderItem item = new OrderItem(
                UUID.randomUUID(), "Mouse",
                new BigDecimal("50000"), 1);

        Order order = new Order(
                UUID.randomUUID(), UUID.randomUUID(), List.of(item));

        assertThrows(IllegalStateException.class, order::dispatch);
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
    }
}
