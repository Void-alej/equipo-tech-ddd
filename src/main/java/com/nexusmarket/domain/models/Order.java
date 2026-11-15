
package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Order implements java.io.Serializable {

    private final UUID id;
    private final UUID buyerId;
    private final List<OrderItem> items;
    private final LocalDateTime createdAt;

    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private final Set<UUID> digitallyDeliveredProductIds = new HashSet<>();

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
            status = OrderStatus.CANCELLED;
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

    public void markDigitalDelivered() {
        ensureNotFinalized();

        if (status != OrderStatus.PAID
                || paymentStatus != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                    "Solo se puede entregar digitalmente un pedido pagado.");
        }

        status = OrderStatus.DELIVERED;
    }

    /** Registra la entrega inmediata de un artículo digital después del pago. */
    public void markDigitalItemDelivered(UUID productId) {
        ensureNotFinalized();
        if (status != OrderStatus.PAID || paymentStatus != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                    "Los artículos digitales solo se entregan después del pago aprobado.");
        }
        if (productId == null || items.stream().noneMatch(
                item -> item.getProductId().equals(productId))) {
            throw new IllegalArgumentException(
                    "El producto digital debe pertenecer al pedido.");
        }
        digitallyDeliveredProductIds.add(productId);
    }

    public Set<UUID> getDigitallyDeliveredProductIds() {
        return Set.copyOf(digitallyDeliveredProductIds);
    }

    public boolean isDigitalItemDelivered(UUID productId) {
        return productId != null && digitallyDeliveredProductIds.contains(productId);
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

    public void transitionAndPersist(
            Runnable transition,
            Runnable persistence) {

        if (transition == null || persistence == null) {
            throw new IllegalArgumentException(
                    "La transición y la persistencia son obligatorias.");
        }

        OrderStatus previousStatus = status;
        PaymentStatus previousPaymentStatus = paymentStatus;
        Set<UUID> previousDigitalDeliveries = new HashSet<>(digitallyDeliveredProductIds);

        try {
            transition.run();
            persistence.run();
        } catch (RuntimeException exception) {
            status = previousStatus;
            paymentStatus = previousPaymentStatus;
            digitallyDeliveredProductIds.clear();
            digitallyDeliveredProductIds.addAll(previousDigitalDeliveries);
            throw exception;
        }
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
