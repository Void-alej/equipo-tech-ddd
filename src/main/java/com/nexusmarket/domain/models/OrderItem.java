package com.nexusmarket.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderItem {

    private final UUID productId;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int quantity;

    public OrderItem(
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            int quantity) {

        if (productId == null) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        }

        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
