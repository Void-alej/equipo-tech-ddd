package com.nexusmarket.domain.models;

import java.util.UUID;

public final class ShipmentItem {

    private final UUID productId;
    private final String productName;
    private final int quantity;

    public ShipmentItem(
            UUID productId,
            String productName,
            int quantity) {

        if (productId == null) {
            throw new IllegalArgumentException(
                    "El identificador del producto es obligatorio.");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del producto es obligatorio.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero.");
        }

        this.productId = productId;
        this.productName = productName.trim();
        this.quantity = quantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }
}
