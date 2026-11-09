package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.ShipmentStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Shipment {

    private final UUID id;
    private final UUID orderId;
    private final UUID warehouseId;
    private final List<ShipmentItem> items;
    private final LocalDateTime createdAt;

    private final List<ShipmentTrackingEvent> trackingHistory =
            new ArrayList<>();

    private ShipmentStatus status;

    public Shipment(
            UUID id,
            UUID orderId,
            UUID warehouseId,
            List<ShipmentItem> items) {

        if (id == null || orderId == null || warehouseId == null) {
            throw new IllegalArgumentException(
                    "Los identificadores del envío, pedido y bodega son obligatorios.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "El envío debe contener al menos un producto.");
        }
        if (items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException(
                    "El envío no puede contener productos nulos.");
        }

        this.id = id;
        this.orderId = orderId;
        this.warehouseId = warehouseId;
        this.items = List.copyOf(items);
        this.createdAt = LocalDateTime.now();
        this.status = ShipmentStatus.PREPARING;
    }

    public synchronized void pack(
            UUID actorId, String description) {
        transition(
                ShipmentStatus.PREPARING,
                ShipmentStatus.PACKED,
                actorId,
                description
        );
    }

    public synchronized void dispatch(
            UUID actorId, String description) {
        transition(
                ShipmentStatus.PACKED,
                ShipmentStatus.DISPATCHED,
                actorId,
                description
        );
    }

    public synchronized void markInTransit(
            UUID actorId, String description) {
        transition(
                ShipmentStatus.DISPATCHED,
                ShipmentStatus.IN_TRANSIT,
                actorId,
                description
        );
    }

    public synchronized void deliver(
            UUID actorId, String description) {
        transition(
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.DELIVERED,
                actorId,
                description
        );
    }

    /**
     * Ejecuta una transición y su persistencia como una sola operación
     * desde la perspectiva del agregado en memoria.
     */
    public synchronized void transitionAndPersist(
            Runnable transition,
            Runnable persistence) {

        if (transition == null || persistence == null) {
            throw new IllegalArgumentException(
                    "La transición y la persistencia son obligatorias.");
        }

        ShipmentStatus previousStatus = status;
        int previousHistorySize = trackingHistory.size();

        try {
            transition.run();
            persistence.run();
        } catch (RuntimeException exception) {
            status = previousStatus;

            while (trackingHistory.size() > previousHistorySize) {
                trackingHistory.remove(trackingHistory.size() - 1);
            }

            throw exception;
        }
    }

    private void transition(
            ShipmentStatus expectedStatus,
            ShipmentStatus nextStatus,
            UUID actorId,
            String description) {

        if (actorId == null) {
            throw new IllegalArgumentException(
                    "El operador responsable es obligatorio.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "La descripción del seguimiento es obligatoria.");
        }
        if (status != expectedStatus) {
            throw new IllegalStateException(
                    "No se puede cambiar el envío de "
                            + status + " a " + nextStatus + ".");
        }

        ShipmentTrackingEvent event =
                new ShipmentTrackingEvent(
                        nextStatus,
                        actorId,
                        description
                );

        status = nextStatus;
        trackingHistory.add(event);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public List<ShipmentItem> getItems() {
        return items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public synchronized ShipmentStatus getStatus() {
        return status;
    }

    public synchronized List<ShipmentTrackingEvent> getTrackingHistory() {
        return List.copyOf(trackingHistory);
    }
}
