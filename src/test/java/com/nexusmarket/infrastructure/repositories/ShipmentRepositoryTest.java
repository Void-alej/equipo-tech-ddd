package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.models.ShipmentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentRepositoryTest {

    private InMemoryShipmentRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryShipmentRepository();
    }

    @Test
    void shouldSaveAndFindShipmentByOrder() {
        UUID orderId = UUID.randomUUID();
        Shipment shipment = createShipment(orderId, UUID.randomUUID());

        repository.save(shipment);

        assertEquals(shipment, repository.findById(shipment.getId()).orElseThrow());
        assertEquals(1, repository.findByOrderId(orderId).size());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldAllowDifferentWarehousesForSameOrder() {
        UUID orderId = UUID.randomUUID();

        Shipment first = createShipment(orderId, UUID.randomUUID());
        Shipment second = createShipment(orderId, UUID.randomUUID());

        repository.saveAll(List.of(first, second));

        assertEquals(2, repository.findByOrderId(orderId).size());
    }

    @Test
    void shouldRejectDuplicateShipmentForSameOrderAndWarehouse() {
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Shipment first = createShipment(orderId, warehouseId);
        Shipment duplicate = createShipment(orderId, warehouseId);

        assertThrows(
                IllegalStateException.class,
                () -> repository.saveAll(List.of(first, duplicate))
        );

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void shouldRejectSecondShipmentForExistingOrderAndWarehouse() {
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        repository.save(createShipment(orderId, warehouseId));

        assertThrows(
                IllegalStateException.class,
                () -> repository.save(createShipment(orderId, warehouseId))
        );

        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldRejectBatchContainingDifferentOrders() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.saveAll(List.of(
                        createShipment(UUID.randomUUID(), UUID.randomUUID()),
                        createShipment(UUID.randomUUID(), UUID.randomUUID())
                ))
        );

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void shouldRejectBatchWithDuplicateIds() {
        UUID id = UUID.randomUUID();

        Shipment first = new Shipment(
                id, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ShipmentItem(
                        UUID.randomUUID(), "Producto A", 1))
        );

        Shipment second = new Shipment(
                id, first.getOrderId(), UUID.randomUUID(),
                List.of(new ShipmentItem(
                        UUID.randomUUID(), "Producto B", 1))
        );

        assertThrows(
                IllegalStateException.class,
                () -> repository.saveAll(List.of(first, second))
        );

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void shouldAllowUpdatingExistingShipmentWithoutChangingItsOrigin() {
        Shipment shipment =
                createShipment(UUID.randomUUID(), UUID.randomUUID());

        repository.save(shipment);

        UUID actorId = UUID.randomUUID();
        shipment.pack(actorId, "Empacado.");
        repository.save(shipment);

        assertEquals(
                com.nexusmarket.domain.enums.ShipmentStatus.PACKED,
                repository.findById(shipment.getId())
                        .orElseThrow()
                        .getStatus()
        );

        assertThrows(
                IllegalStateException.class,
                () -> repository.save(new Shipment(
                        shipment.getId(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of(new ShipmentItem(
                                UUID.randomUUID(), "Otro producto", 1))
                ))
        );
    }

    @Test
    void shouldAcceptEmptyBatch() {
        assertDoesNotThrow(() -> repository.saveAll(List.of()));
        assertTrue(repository.findAll().isEmpty());
    }

    private Shipment createShipment(UUID orderId, UUID warehouseId) {
        return new Shipment(
                UUID.randomUUID(),
                orderId,
                warehouseId,
                List.of(
                        new ShipmentItem(
                                UUID.randomUUID(),
                                "Producto de prueba",
                                2
                        )
                )
        );
    }
}
