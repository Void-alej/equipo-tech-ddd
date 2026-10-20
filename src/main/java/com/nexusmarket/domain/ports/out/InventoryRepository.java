package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.Inventory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository {
    Inventory save(Inventory inventory);
    Optional<Inventory> findById(UUID id);
    Optional<Inventory> findByProductAndWarehouse(
            UUID productId, UUID warehouseId);
    List<Inventory> findAll();
    List<Inventory> findByWarehouseId(UUID warehouseId);
    List<Inventory> findByProductId(UUID productId);
}
