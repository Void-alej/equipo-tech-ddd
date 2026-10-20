package com.nexusmarket.domain.models;

import java.math.BigDecimal;
import java.util.UUID;

public class CartItem {

    private final UUID productId;
    private final String productName;
    private final BigDecimal unitPrice;
    private int quantity;

    public CartItem(
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

    public void increaseQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("El incremento debe ser mayor que cero.");
        }
        quantity += amount;
    }

    public void decreaseQuantity(int amount) {
        if (amount <= 0 || amount >= quantity) {
            throw new IllegalArgumentException(
                    "La reducción debe ser positiva y menor que la cantidad actual.");
        }
        quantity -= amount;
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
