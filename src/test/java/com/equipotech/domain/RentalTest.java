package com.equipotech.domain;

import com.equipotech.domain.enums.RentalStatus;
import com.equipotech.domain.models.Customer;
import com.equipotech.domain.models.Equipment;
import com.equipotech.domain.models.Rental;
import com.equipotech.domain.valueobjects.Email;
import com.equipotech.domain.valueobjects.Money;
import com.equipotech.domain.valueobjects.Phone;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RentalTest {

    @Test
    void shouldCalculateRentalTotal() {

        Customer customer = new Customer(
                "Ana",
                new Email("ana@test.com"),
                new Phone("+573001112233")
        );

        Equipment equipment = new Equipment(
                "Laptop",
                "Computadores",
                new Money(50000)
        );

        Rental rental = new Rental(
                customer,
                equipment,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3)
        );

        assertEquals(
                new Money(150000).amount(),
                rental.calculateTotal().amount()
        );
    }

    @Test
    void shouldChangeStateWhenEquipmentIsReturned() {

        Customer customer = new Customer(
                "Ana",
                new Email("ana@test.com"),
                new Phone("+573001112233")
        );

        Equipment equipment = new Equipment(
                "Laptop",
                "Computadores",
                new Money(50000)
        );

        Rental rental = new Rental(
                customer,
                equipment,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1)
        );

        rental.confirm();
        rental.activate();
        rental.returnEquipment();

        assertEquals(
                RentalStatus.RETURNED,
                rental.getStatus()
        );
    }

    @Test
    void shouldRejectInvalidDates() {

        Customer customer = new Customer(
                "Ana",
                new Email("ana@test.com"),
                new Phone("+573001112233")
        );

        Equipment equipment = new Equipment(
                "Laptop",
                "Computadores",
                new Money(50000)
        );

        assertThrows(
                RuntimeException.class,
                () -> new Rental(
                        customer,
                        equipment,
                        LocalDate.of(2026, 9, 5),
                        LocalDate.of(2026, 9, 1)
                )
        );
    }
}