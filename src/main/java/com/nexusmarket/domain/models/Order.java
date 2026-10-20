package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Order {

    private final UUID id;
    private final UUID buyerId;
    private final List<OrderItem> items;
    private final LocalDateTime createdAt;

    private OrderStatus status;
    private PaymentStatus paymentStatus;

    public Order(UUID id, UUID buyerId, List<OrderItem> items) {
        if (id == null || buyerId == null) {
            throw new IllegalArgumentException(
                    "El pedido y el comprador deben tener identificadores.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "El pedido debe contener al menos un producto.");
        }
        if (items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException(
                    "El pedido no puede contener productos nulos.");
        }

        this.id = id;
        this.buyerId = buyerId;
        this.items = new ArrayList<>(items);
        this.createdAt = LocalDateTime.now();
        this.status = OrderStatus.PENDING_PAYMENT;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void registerPayment(boolean approved) {
        ensureNotFinalized();

        if (status != OrderStatus.PENDING_PAYMENT
                || paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "El pedido no está pendiente de pago.");
        }

        if (approved) {
            paymentStatus = PaymentStatus.APPROVED;
            status = OrderStatus.PAID;
        } else {
            paymentStatus = PaymentStatus.REJECTED;
        }
    }

    public void dispatch() {
        ensureNotFinalized();

        if (status != OrderStatus.PAID) {
            throw new IllegalStateException(
                    "Solo se puede despachar un pedido pagado.");
        }

        status = OrderStatus.DISPATCHED;
    }

    public void markDelivered() {
        ensureNotFinalized();

        if (status != OrderStatus.DISPATCHED) {
            throw new IllegalStateException(
                    "Solo se puede entregar un pedido despachado.");
        }

        status = OrderStatus.DELIVERED;
    }

    public void finalizeOrder() {
        ensureNotFinalized();

        if (status != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Solo se puede finalizar un pedido entregado.");
        }

        status = OrderStatus.FINALIZED;
    }

    public void cancel() {
        ensureNotFinalized();

        if (status != OrderStatus.PENDING_PAYMENT
                && status != OrderStatus.PAID) {
            throw new IllegalStateException(
                    "El pedido no puede cancelarse en su estado actual.");
        }

        if (paymentStatus == PaymentStatus.APPROVED) {
            paymentStatus = PaymentStatus.REFUNDED;
        } else if (paymentStatus == PaymentStatus.PENDING) {
            paymentStatus = PaymentStatus.REJECTED;
        }

        status = OrderStatus.CANCELLED;
    }

    private void ensureNotFinalized() {
        if (status == OrderStatus.FINALIZED
                || status == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Un pedido finalizado o cancelado no puede modificarse.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }
}
