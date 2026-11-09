package com.nexusmarket.domain;

import com.nexusmarket.domain.enums.ShipmentStatus;
import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.models.ShipmentItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentTest {

    private Shipment shipment;
    private UUID logisticsId;

    @BeforeEach
    void setUp() {
        logisticsId = UUID.randomUUID();

        shipment = new Shipment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        new ShipmentItem(
                                UUID.randomUUID(),
                                "Teclado",
                                2
                        ),
                        new ShipmentItem(
                                UUID.randomUUID(),
                                "Mouse",
                                1
                        )
                )
        );
    }

    @Test
    void shouldStartInPreparingStatus() {
        assertEquals(ShipmentStatus.PREPARING, shipment.getStatus());
        assertTrue(shipment.getTrackingHistory().isEmpty());
        assertEquals(2, shipment.getItems().size());
    }

    @Test
    void shouldFollowCompleteShipmentLifecycle() {
        shipment.pack(logisticsId, "Pedido empacado.");
        assertEquals(ShipmentStatus.PACKED, shipment.getStatus());

        shipment.dispatch(logisticsId, "Pedido despachado.");
        assertEquals(ShipmentStatus.DISPATCHED, shipment.getStatus());

        shipment.markInTransit(logisticsId, "Pedido en transporte.");
        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.getStatus());

        shipment.deliver(logisticsId, "Pedido entregado.");
        assertEquals(ShipmentStatus.DELIVERED, shipment.getStatus());

        assertEquals(4, shipment.getTrackingHistory().size());
    }

    @Test
    void shouldRecordActorAndDescriptionInTrackingHistory() {
        shipment.pack(logisticsId, "Empaque verificado.");

        var event = shipment.getTrackingHistory().get(0);

        assertEquals(ShipmentStatus.PACKED, event.getStatus());
        assertEquals(logisticsId, event.getActorId());
        assertEquals("Empaque verificado.", event.getDescription());
        assertNotNull(event.getChangedAt());
    }

    @Test
    void shouldRejectSkippingShipmentStages() {
        assertThrows(
                IllegalStateException.class,
                () -> shipment.dispatch(logisticsId, "Despacho sin empaque.")
        );

        assertEquals(ShipmentStatus.PREPARING, shipment.getStatus());
        assertTrue(shipment.getTrackingHistory().isEmpty());
    }

    @Test
    void shouldRejectRepeatedTransition() {
        shipment.pack(logisticsId, "Empacado.");

        assertThrows(
                IllegalStateException.class,
                () -> shipment.pack(logisticsId, "Segundo empaque.")
        );

        assertEquals(1, shipment.getTrackingHistory().size());
    }

    @Test
    void shouldRejectTransitionWithoutActor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> shipment.pack(null, "Empacado.")
        );

        assertEquals(ShipmentStatus.PREPARING, shipment.getStatus());
    }

    @Test
    void shouldRejectTransitionWithoutDescription() {
        assertThrows(
                IllegalArgumentException.class,
                () -> shipment.pack(logisticsId, "  ")
        );

        assertEquals(ShipmentStatus.PREPARING, shipment.getStatus());
    }

    @Test
    void shouldExposeImmutableCollections() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> shipment.getItems().clear()
        );

        shipment.pack(logisticsId, "Empacado.");

        assertThrows(
                UnsupportedOperationException.class,
                () -> shipment.getTrackingHistory().clear()
        );
    }
}
