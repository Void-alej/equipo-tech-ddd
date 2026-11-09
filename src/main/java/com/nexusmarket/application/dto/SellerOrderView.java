package com.nexusmarket.application.dto;

import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;
import com.nexusmarket.domain.models.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Vista de un pedido que contiene exclusivamente los artículos
 * pertenecientes a un vendedor.
 */
public record SellerOrderView(
        UUID orderId,
        OrderStatus status,
        PaymentStatus paymentStatus,
        LocalDateTime createdAt,
        List<OrderItem> items) {

    public SellerOrderView {
        if (orderId == null || status == null
                || paymentStatus == null || createdAt == null) {
            throw new IllegalArgumentException(
                    "Los datos del pedido son obligatorios.");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "La vista debe contener artículos del vendedor.");
        }

        items = List.copyOf(items);
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
