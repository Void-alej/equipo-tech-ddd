package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInventoryRepositoryTest {

    private InMemoryInventoryRepository repository;

    private UUID productId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryInventoryRepository();
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
    }

    @Test
    void shouldStoreAnIndependentCopyOfInventory() {
        Inventory original = new Inventory(
                UUID.randomUUID(), productId, warehouseId, 20
        );

        repository.save(original);
        original.addStock(10);

        Inventory stored = repository
                .findById(original.getId())
                .orElseThrow();

        assertEquals(20, stored.getQuantity());
    }

    @Test
    void shouldReturnAnIndependentCopyWhenFindingById() {
        Inventory inventory = createInventory(20);
        repository.save(inventory);

        Inventory retrieved = repository
                .findById(inventory.getId())
                .orElseThrow();

        retrieved.addStock(10);

        Inventory stored = repository
                .findById(inventory.getId())
                .orElseThrow();

        assertEquals(20, stored.getQuantity());
        assertEquals(30, retrieved.getQuantity());
    }

    @Test
    void shouldReturnAnIndependentCopyWhenFindingByProductAndWarehouse() {
        Inventory inventory = createInventory(20);
        repository.save(inventory);

        Inventory retrieved = repository
                .findByProductAndWarehouse(productId, warehouseId)
                .orElseThrow();

        retrieved.addStock(5);

        Inventory stored = repository
                .findById(inventory.getId())
                .orElseThrow();

        assertEquals(20, stored.getQuantity());
    }

    @Test
    void shouldReturnIndependentCopiesInFindAll() {
        Inventory inventory = createInventory(20);
        repository.save(inventory);

        List<Inventory> retrieved = repository.findAll();
        retrieved.get(0).addStock(5);

        assertEquals(
                20,
                repository.findById(inventory.getId())
                        .orElseThrow()
                        .getQuantity()
        );
    }

    @Test
    void shouldReturnIndependentCopiesInFindByWarehouseId() {
        Inventory inventory = createInventory(20);
        repository.save(inventory);

        repository.findByWarehouseId(warehouseId)
                .get(0)
                .addStock(5);

        assertEquals(
                20,
                repository.findById(inventory.getId())
                        .orElseThrow()
                        .getQuantity()
        );
    }

    @Test
    void shouldReturnIndependentCopiesInFindByProductId() {
        Inventory inventory = createInventory(20);
        repository.save(inventory);

        repository.findByProductId(productId)
                .get(0)
                .addStock(5);

        assertEquals(
                20,
                repository.findById(inventory.getId())
                        .orElseThrow()
                        .getQuantity()
        );
    }

    private Inventory createInventory(int quantity) {
        return new Inventory(
                UUID.randomUUID(),
                productId,
                warehouseId,
                quantity
        );
    }
}
