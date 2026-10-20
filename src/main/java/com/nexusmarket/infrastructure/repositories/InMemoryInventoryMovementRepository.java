package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.InventoryMovement;
import com.nexusmarket.domain.ports.out.InventoryMovementRepository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.UUID;

public class InMemoryInventoryMovementRepository
        implements InventoryMovementRepository {

    private final ConcurrentMap<UUID, InventoryMovement> movements =
            new ConcurrentHashMap<>();

    @Override
    public InventoryMovement save(InventoryMovement movement) {
        if (movement == null) {
            throw new IllegalArgumentException(
                    "El movimiento no puede ser null");
        }

        movements.put(movement.getId(), movement);
        return movement;
    }

    @Override
    public List<InventoryMovement> findByInventoryId(UUID inventoryId) {
        return movements.values().stream()
                .filter(m -> m.getInventoryId().equals(inventoryId))
                .sorted(Comparator.comparing(
                        InventoryMovement::getOccurredAt))
                .toList();
    }

    @Override
    public List<InventoryMovement> findAll() {
        return movements.values().stream()
                .sorted(Comparator.comparing(
                        InventoryMovement::getOccurredAt))
                .toList();
    }
}
