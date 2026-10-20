package com.nexusmarket.domain.models;

import java.util.UUID;

public class Inventory {

    private final UUID id;
    private final UUID productId;
    private final UUID warehouseId;
    private int quantity;
    private int reservedQuantity;

    public Inventory(
            UUID id,
            UUID productId,
            UUID warehouseId,
            int initialQuantity
    ) {
        if (id == null || productId == null || warehouseId == null) {
            throw new IllegalArgumentException(
                    "Los identificadores de inventario, producto y bodega son obligatorios."
            );
        }
        if (initialQuantity < 0) {
            throw new IllegalArgumentException(
                    "La cantidad inicial no puede ser negativa."
            );
        }

        this.id = id;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = initialQuantity;
        this.reservedQuantity = 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public void addStock(int amount) {
        requirePositive(amount);
        quantity = Math.addExact(quantity, amount);
    }

    public void reserve(int amount) {
        requirePositive(amount);

        if (amount > getAvailableQuantity()) {
            throw new IllegalStateException(
                    "No hay suficientes unidades disponibles para reservar."
            );
        }

        reservedQuantity = Math.addExact(reservedQuantity, amount);
    }

    public void releaseReservation(int amount) {
        requirePositive(amount);

        if (amount > reservedQuantity) {
            throw new IllegalStateException(
                    "No se pueden liberar más unidades de las reservadas."
            );
        }

        reservedQuantity -= amount;
    }

    public void registerSale(int amount) {
        requirePositive(amount);

        if (amount > reservedQuantity) {
            throw new IllegalStateException(
                    "La venta no puede superar las unidades reservadas."
            );
        }

        quantity -= amount;
        reservedQuantity -= amount;
    }

    public void adjustStock(int newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException(
                    "El inventario no puede ser negativo."
            );
        }
        if (newQuantity < reservedQuantity) {
            throw new IllegalStateException(
                    "El ajuste no puede quedar por debajo de las unidades reservadas."
            );
        }

        quantity = newQuantity;
    }

    public void registerReturn(int amount) {
        requirePositive(amount);
        quantity = Math.addExact(quantity, amount);
    }

    private void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero."
            );
        }
    }
}
