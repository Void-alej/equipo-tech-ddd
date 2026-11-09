package com.nexusmarket.domain.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro histórico de la asignación de un producto
 * a una bodega dentro de un pedido.
 */
public final class OrderAllocation {

    private final UUID id;
    private final UUID orderId;
    private final UUID productId;
    private final String productName;
    private final UUID warehouseId;
    private final int quantity;
    private final LocalDateTime allocatedAt;

    public OrderAllocation(
            UUID id,
            UUID orderId,
            UUID productId,
            String productName,
            UUID warehouseId,
            int quantity) {

        if (id == null || orderId == null
                || productId == null || warehouseId == null) {
            throw new IllegalArgumentException(
                    "Los identificadores de la asignación son obligatorios.");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del producto es obligatorio.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad asignada debe ser mayor que cero.");
        }

        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName.trim();
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.allocatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getAllocatedAt() {
        return allocatedAt;
    }
}
