package com.nexusmarket.domain;

import com.nexusmarket.domain.models.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTest {

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                10
        );
    }

    @Test
    void shouldReserveAvailableStock() {
        inventory.reserve(3);

        assertEquals(10, inventory.getQuantity());
        assertEquals(3, inventory.getReservedQuantity());
        assertEquals(7, inventory.getAvailableQuantity());
    }

    @Test
    void shouldRejectReservationAboveAvailableStock() {
        assertThrows(
                IllegalStateException.class,
                () -> inventory.reserve(11)
        );
    }

    @Test
    void shouldRegisterSaleFromReservedStock() {
        inventory.reserve(4);
        inventory.registerSale(3);

        assertEquals(7, inventory.getQuantity());
        assertEquals(1, inventory.getReservedQuantity());
        assertEquals(6, inventory.getAvailableQuantity());
    }

    @Test
    void shouldRejectSaleAboveReservedStock() {
        assertThrows(
                IllegalStateException.class,
                () -> inventory.registerSale(1)
        );
    }

    @Test
    void shouldRejectNegativeAdjustment() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inventory.adjustStock(-1)
        );
    }

    @Test
    void shouldNotAdjustStockBelowReservedQuantity() {
        inventory.reserve(5);

        assertThrows(
                IllegalStateException.class,
                () -> inventory.adjustStock(4)
        );
    }

    @Test
    void shouldReleaseReservation() {
        inventory.reserve(4);
        inventory.releaseReservation(2);

        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());
    }

    @Test
    void shouldRegisterReturnedStock() {
        inventory.registerReturn(2);

        assertEquals(12, inventory.getQuantity());
        assertEquals(12, inventory.getAvailableQuantity());
    }
}
