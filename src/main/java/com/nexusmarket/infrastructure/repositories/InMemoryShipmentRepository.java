package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.ports.out.ShipmentRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryShipmentRepository implements ShipmentRepository {

    private record ShipmentKey(UUID orderId, UUID warehouseId) {}

    private final ConcurrentMap<UUID, Shipment> shipments =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<ShipmentKey, UUID> shipmentIdsByOrderWarehouse =
            new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Shipment shipment) {
        if (shipment == null) {
            throw new IllegalArgumentException("El envío es obligatorio.");
        }

        ShipmentKey key = new ShipmentKey(
                shipment.getOrderId(),
                shipment.getWarehouseId()
        );

        UUID existingId = shipmentIdsByOrderWarehouse.get(key);

        if (existingId != null && !existingId.equals(shipment.getId())) {
            throw new IllegalStateException(
                    "Ya existe un envío para ese pedido y esa bodega.");
        }

        Shipment existingShipment = shipments.get(shipment.getId());

        if (existingShipment != null) {
            if (!existingShipment.getOrderId().equals(shipment.getOrderId())
                    || !existingShipment.getWarehouseId()
                            .equals(shipment.getWarehouseId())) {
                throw new IllegalStateException(
                        "No se puede cambiar el pedido o la bodega de un envío.");
            }
        }

        shipments.put(shipment.getId(), shipment);
        shipmentIdsByOrderWarehouse.put(key, shipment.getId());
    }

    @Override
    public synchronized void saveAll(List<Shipment> shipmentsToSave) {
        if (shipmentsToSave == null) {
            throw new IllegalArgumentException(
                    "La lista de envíos es obligatoria.");
        }

        if (shipmentsToSave.isEmpty()) {
            return;
        }

        Set<UUID> batchIds = new HashSet<>();
        Set<ShipmentKey> batchKeys = new HashSet<>();
        Set<UUID> batchOrderIds = new HashSet<>();

        // Validación completa del lote antes de persistir.
        for (Shipment shipment : shipmentsToSave) {
            if (shipment == null) {
                throw new IllegalArgumentException(
                        "La lista no puede contener envíos nulos.");
            }

            if (!batchIds.add(shipment.getId())
                    || this.shipments.containsKey(shipment.getId())) {
                throw new IllegalStateException(
                        "Ya existe un envío con ese identificador.");
            }

            ShipmentKey key = new ShipmentKey(
                    shipment.getOrderId(),
                    shipment.getWarehouseId()
            );

            if (!batchKeys.add(key)
                    || shipmentIdsByOrderWarehouse.containsKey(key)) {
                throw new IllegalStateException(
                        "Ya existe un envío para ese pedido y esa bodega.");
            }

            batchOrderIds.add(shipment.getOrderId());
        }

        if (batchOrderIds.size() != 1) {
            throw new IllegalArgumentException(
                    "Todos los envíos del lote deben pertenecer al mismo pedido.");
        }

        // Guardado únicamente después de validar todos los elementos.
        for (Shipment shipment : shipmentsToSave) {
            ShipmentKey key = new ShipmentKey(
                    shipment.getOrderId(),
                    shipment.getWarehouseId()
            );

            this.shipments.put(shipment.getId(), shipment);
            shipmentIdsByOrderWarehouse.put(key, shipment.getId());
        }
    }

    @Override
    public Optional<Shipment> findById(UUID id) {
        return Optional.ofNullable(shipments.get(id));
    }

    @Override
    public List<Shipment> findByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }

        return shipments.values().stream()
                .filter(shipment -> shipment.getOrderId().equals(orderId))
                .sorted(Comparator.comparing(Shipment::getCreatedAt))
                .toList();
    }

    @Override
    public List<Shipment> findAll() {
        return shipments.values().stream()
                .sorted(Comparator.comparing(Shipment::getCreatedAt))
                .toList();
    }
}
