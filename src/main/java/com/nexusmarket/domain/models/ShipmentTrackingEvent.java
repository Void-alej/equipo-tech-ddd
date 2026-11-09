package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.ShipmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public final class ShipmentTrackingEvent {

    private final UUID id;
    private final ShipmentStatus status;
    private final UUID actorId;
    private final String description;
    private final LocalDateTime changedAt;

    public ShipmentTrackingEvent(
            ShipmentStatus status,
            UUID actorId,
            String description) {

        if (status == null || actorId == null) {
            throw new IllegalArgumentException(
                    "El estado y el operador son obligatorios.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "La descripción del seguimiento es obligatoria.");
        }

        this.id = UUID.randomUUID();
        this.status = status;
        this.actorId = actorId;
        this.description = description.trim();
        this.changedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
