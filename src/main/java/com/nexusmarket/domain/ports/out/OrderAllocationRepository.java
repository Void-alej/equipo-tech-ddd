package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.OrderAllocation;

import java.util.List;
import java.util.UUID;

public interface OrderAllocationRepository {

    /**
     * Guarda de forma conjunta las asignaciones de un pedido.
     * Una operación vacía es válida para pedidos sin productos físicos.
     */
    void saveAll(List<OrderAllocation> allocations);

    List<OrderAllocation> findByOrderId(UUID orderId);

    List<OrderAllocation> findAll();

    void deleteByOrderId(UUID orderId);
}
