package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.OrderAllocation;
import com.nexusmarket.domain.ports.out.OrderAllocationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryOrderAllocationRepository
        implements OrderAllocationRepository {

    private final ConcurrentMap<UUID, OrderAllocation> allocationsById =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, List<UUID>> allocationIdsByOrder =
            new ConcurrentHashMap<>();

    private record AllocationKey(
            UUID orderId,
            UUID productId,
            UUID warehouseId) {}

    @Override
    public synchronized void saveAll(List<OrderAllocation> allocations) {
        if (allocations == null) {
            throw new IllegalArgumentException(
                    "La lista de asignaciones es obligatoria.");
        }

        if (allocations.isEmpty()) {
            return;
        }

        Set<UUID> batchIds = new HashSet<>();
        Set<UUID> batchOrderIds = new HashSet<>();
        Set<AllocationKey> batchKeys = new HashSet<>();

        // Validar todo el lote antes de guardar cualquier elemento.
        for (OrderAllocation allocation : allocations) {
            if (allocation == null) {
                throw new IllegalArgumentException(
                        "La lista no puede contener asignaciones nulas.");
            }

            if (!batchIds.add(allocation.getId())
                    || allocationsById.containsKey(allocation.getId())) {
                throw new IllegalStateException(
                        "Ya existe una asignación con ese identificador.");
            }

            batchOrderIds.add(allocation.getOrderId());

            AllocationKey key = new AllocationKey(
                    allocation.getOrderId(),
                    allocation.getProductId(),
                    allocation.getWarehouseId()
            );

            if (!batchKeys.add(key)) {
                throw new IllegalStateException(
                        "El lote contiene asignaciones duplicadas.");
            }
        }

        if (batchOrderIds.size() != 1) {
            throw new IllegalArgumentException(
                    "Todas las asignaciones deben pertenecer al mismo pedido.");
        }

        UUID orderId = batchOrderIds.iterator().next();

        if (allocationIdsByOrder.containsKey(orderId)) {
            throw new IllegalStateException(
                    "El pedido ya tiene asignaciones registradas.");
        }

        // Confirmar el lote después de superar todas las validaciones.
        List<UUID> savedIds = new ArrayList<>();

        for (OrderAllocation allocation : allocations) {
            allocationsById.put(allocation.getId(), allocation);
            savedIds.add(allocation.getId());
        }

        allocationIdsByOrder.put(orderId, List.copyOf(savedIds));
    }

    @Override
    public List<OrderAllocation> findByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }

        List<UUID> ids = allocationIdsByOrder.get(orderId);

        if (ids == null) {
            return List.of();
        }

        return ids.stream()
                .map(allocationsById::get)
                .filter(allocation -> allocation != null)
                .sorted(Comparator.comparing(
                        OrderAllocation::getAllocatedAt))
                .toList();
    }

    @Override
    public List<OrderAllocation> findAll() {
        return allocationsById.values().stream()
                .sorted(Comparator.comparing(
                        OrderAllocation::getAllocatedAt))
                .toList();
    }

    @Override
    public synchronized void deleteByOrderId(UUID orderId) {
        if (orderId == null) {
            return;
        }

        List<UUID> ids = allocationIdsByOrder.remove(orderId);

        if (ids != null) {
            ids.forEach(allocationsById::remove);
        }
    }
}
