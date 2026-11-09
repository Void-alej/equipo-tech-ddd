package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.OrderAllocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderAllocationRepositoryTest {

    private InMemoryOrderAllocationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderAllocationRepository();
    }

    @Test
    void shouldSaveAndFindAllocationsByOrder() {
        UUID orderId = UUID.randomUUID();

        OrderAllocation first = allocation(
                orderId, UUID.randomUUID(), UUID.randomUUID(), 2);

        OrderAllocation second = allocation(
                orderId, UUID.randomUUID(), UUID.randomUUID(), 3);

        repository.saveAll(List.of(first, second));

        List<OrderAllocation> result =
                repository.findByOrderId(orderId);

        assertEquals(2, result.size());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void shouldPreserveAllocationsFromDifferentWarehouses() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        UUID warehouseA = UUID.randomUUID();
        UUID warehouseB = UUID.randomUUID();

        repository.saveAll(List.of(
                allocation(orderId, productId, warehouseA, 2),
                allocation(orderId, productId, warehouseB, 3)
        ));

        List<OrderAllocation> result =
                repository.findByOrderId(orderId);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(
                item -> item.getWarehouseId().equals(warehouseA)
                        && item.getQuantity() == 2));
        assertTrue(result.stream().anyMatch(
                item -> item.getWarehouseId().equals(warehouseB)
                        && item.getQuantity() == 3));
    }

    @Test
    void shouldRejectDuplicateAllocationForSameProductAndWarehouse() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        OrderAllocation first =
                allocation(orderId, productId, warehouseId, 2);

        OrderAllocation duplicate =
                allocation(orderId, productId, warehouseId, 3);

        assertThrows(
                IllegalStateException.class,
                () -> repository.saveAll(List.of(first, duplicate))
        );

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void shouldRejectSecondBatchForSameOrder() {
        UUID orderId = UUID.randomUUID();

        repository.saveAll(List.of(
                allocation(orderId, UUID.randomUUID(),
                        UUID.randomUUID(), 1)
        ));

        assertThrows(
                IllegalStateException.class,
                () -> repository.saveAll(List.of(
                        allocation(orderId, UUID.randomUUID(),
                                UUID.randomUUID(), 1)
                ))
        );

        assertEquals(1, repository.findByOrderId(orderId).size());
    }

    @Test
    void shouldRejectBatchContainingDifferentOrders() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.saveAll(List.of(
                        allocation(UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID(), 1),
                        allocation(UUID.randomUUID(), UUID.randomUUID(),
                                UUID.randomUUID(), 1)
                ))
        );

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void shouldDeleteOnlyAllocationsOfRequestedOrder() {
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();

        repository.saveAll(List.of(
                allocation(firstOrder, UUID.randomUUID(),
                        UUID.randomUUID(), 1)
        ));

        repository.saveAll(List.of(
                allocation(secondOrder, UUID.randomUUID(),
                        UUID.randomUUID(), 2)
        ));

        repository.deleteByOrderId(firstOrder);

        assertTrue(repository.findByOrderId(firstOrder).isEmpty());
        assertEquals(1, repository.findByOrderId(secondOrder).size());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldAllowEmptyBatchForOrdersWithoutPhysicalProducts() {
        assertDoesNotThrow(() -> repository.saveAll(List.of()));
        assertTrue(repository.findAll().isEmpty());
    }

    private OrderAllocation allocation(
            UUID orderId,
            UUID productId,
            UUID warehouseId,
            int quantity) {

        return new OrderAllocation(
                UUID.randomUUID(),
                orderId,
                productId,
                "Producto de prueba",
                warehouseId,
                quantity
        );
    }
}
