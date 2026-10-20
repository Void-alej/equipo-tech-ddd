package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryWarehouseRepository implements WarehouseRepository {

    private final ConcurrentMap<UUID, Warehouse> warehouses =
            new ConcurrentHashMap<>();

    @Override
    public Warehouse save(Warehouse warehouse) {
        if (warehouse == null) {
            throw new IllegalArgumentException("La bodega no puede ser null");
        }
        warehouses.put(warehouse.getId(), warehouse);
        return warehouse;
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
        return Optional.ofNullable(warehouses.get(id));
    }

    @Override
    public List<Warehouse> findAll() {
        return List.copyOf(warehouses.values());
    }

    @Override
    public List<Warehouse> findBySellerId(UUID sellerId) {
        return warehouses.values().stream()
                .filter(w -> sellerId != null
                        && sellerId.equals(w.getSellerId()))
                .toList();
    }
}
