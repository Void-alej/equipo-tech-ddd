package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.ports.out.InventoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryInventoryRepository implements InventoryRepository {

    private final ConcurrentMap<UUID, Inventory> inventories =
            new ConcurrentHashMap<>();

    @Override
    public Inventory save(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException(
                    "El inventario no puede ser null");
        }

        inventories.put(inventory.getId(), inventory);
        return inventory;
    }

    @Override
    public Optional<Inventory> findById(UUID id) {
        return Optional.ofNullable(inventories.get(id));
    }

    @Override
    public Optional<Inventory> findByProductAndWarehouse(
            UUID productId, UUID warehouseId) {

        return inventories.values().stream()
                .filter(i -> i.getProductId().equals(productId)
                        && i.getWarehouseId().equals(warehouseId))
                .findFirst();
    }

    @Override
    public List<Inventory> findAll() {
        return List.copyOf(inventories.values());
    }

    @Override
    public List<Inventory> findByWarehouseId(UUID warehouseId) {
        return inventories.values().stream()
                .filter(i -> i.getWarehouseId().equals(warehouseId))
                .toList();
    }

    @Override
    public List<Inventory> findByProductId(UUID productId) {
        return inventories.values().stream()
                .filter(i -> i.getProductId().equals(productId))
                .toList();
    }
}
