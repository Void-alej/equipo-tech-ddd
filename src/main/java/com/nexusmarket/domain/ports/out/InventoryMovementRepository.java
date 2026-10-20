package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.InventoryMovement;
import java.util.List;
import java.util.UUID;

public interface InventoryMovementRepository {
    InventoryMovement save(InventoryMovement movement);
    List<InventoryMovement> findByInventoryId(UUID inventoryId);
    List<InventoryMovement> findAll();
}
