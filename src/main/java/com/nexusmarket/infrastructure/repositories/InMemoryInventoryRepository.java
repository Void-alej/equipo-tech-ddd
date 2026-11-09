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

    private final ConcurrentMap<String, UUID> inventoryKeys =
            new ConcurrentHashMap<>();

    @Override
    public synchronized Inventory save(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException(
                    "El inventario no puede ser null");
        }

        Inventory storedInventory = inventory.copy();

        String key = createKey(
                storedInventory.getProductId(),
                storedInventory.getWarehouseId()
        );

        UUID existingId = inventoryKeys.get(key);

        if (existingId != null
                && !existingId.equals(storedInventory.getId())) {
            throw new IllegalStateException(
                    "Ya existe un inventario para ese producto y esa bodega.");
        }

        Inventory existingInventory =
                inventories.get(storedInventory.getId());

        if (existingInventory != null) {
            String previousKey = createKey(
                    existingInventory.getProductId(),
                    existingInventory.getWarehouseId()
            );

            if (!previousKey.equals(key)) {
                inventoryKeys.remove(
                        previousKey, storedInventory.getId());
            }
        }

        inventories.put(storedInventory.getId(), storedInventory);
        inventoryKeys.put(key, storedInventory.getId());

        return storedInventory.copy();
    }

    @Override
    public Optional<Inventory> findById(UUID id) {
        return Optional.ofNullable(inventories.get(id))
                .map(Inventory::copy);
    }

    @Override
    public Optional<Inventory> findByProductAndWarehouse(
            UUID productId, UUID warehouseId) {

        if (productId == null || warehouseId == null) {
            return Optional.empty();
        }

        UUID inventoryId = inventoryKeys.get(
                createKey(productId, warehouseId)
        );

        return inventoryId == null
                ? Optional.empty()
                : findById(inventoryId);
    }

    @Override
    public List<Inventory> findAll() {
        return inventories.values().stream()
                .map(Inventory::copy)
                .toList();
    }

    @Override
    public List<Inventory> findByWarehouseId(UUID warehouseId) {
        return inventories.values().stream()
                .filter(i -> i.getWarehouseId().equals(warehouseId))
                .map(Inventory::copy)
                .toList();
    }

    @Override
    public List<Inventory> findByProductId(UUID productId) {
        return inventories.values().stream()
                .filter(i -> i.getProductId().equals(productId))
                .map(Inventory::copy)
                .toList();
    }

    private String createKey(UUID productId, UUID warehouseId) {
        return productId + ":" + warehouseId;
    }
}
