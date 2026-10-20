package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.Warehouse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository {
    Warehouse save(Warehouse warehouse);
    Optional<Warehouse> findById(UUID id);
    List<Warehouse> findAll();
    List<Warehouse> findBySellerId(UUID sellerId);
}
