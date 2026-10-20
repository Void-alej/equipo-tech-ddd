package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.InventoryMovementType;

import java.time.LocalDateTime;
import java.util.UUID;

public class InventoryMovement {

    private final UUID id;
    private final UUID inventoryId;
    private final InventoryMovementType type;
    private final int quantity;
    private final UUID actorId;
    private final String description;
    private final LocalDateTime occurredAt;

    public InventoryMovement(
            UUID id,
            UUID inventoryId,
            InventoryMovementType type,
            int quantity,
            UUID actorId,
            String description
    ) {
        if (id == null || inventoryId == null || actorId == null) {
            throw new IllegalArgumentException(
                    "Los identificadores del movimiento, inventario y actor son obligatorios."
            );
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad del movimiento debe ser mayor que cero."
            );
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "La descripción del movimiento es obligatoria."
            );
        }

        this.id = id;
        this.inventoryId = inventoryId;
        this.type = type;
        this.quantity = quantity;
        this.actorId = actorId;
        this.description = description.trim();
        this.occurredAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getInventoryId() {
        return inventoryId;
    }

    public InventoryMovementType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
