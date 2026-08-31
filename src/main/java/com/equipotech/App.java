package com.equipotech;

import com.equipotech.domain.models.Customer;
import com.equipotech.domain.models.Equipment;
import com.equipotech.domain.models.Rental;
import com.equipotech.domain.valueobjects.Email;
import com.equipotech.domain.valueobjects.Money;
import com.equipotech.domain.valueobjects.Phone;

import java.time.LocalDate;

public class App {

    public static void main(String[] args) {

        Customer customer = new Customer(
                "Laura Gómez",
                new Email("laura@example.com"),
                new Phone("+573001234567")
        );

        Equipment camera = new Equipment(
                "Cámara profesional",
                "Fotografía",
                new Money(85000)
        );

        Rental rental = new Rental(
                customer,
                camera,
                LocalDate.now(),
                LocalDate.now().plusDays(2)
        );

        rental.confirm();

        rental.activate();

        System.out.println(
                "===== EQUIPOTECH ====="
        );

        System.out.println(
                "Alquiler: " + rental.getId()
        );

        System.out.println(
                "Cliente: " +
                rental.getCustomer().getName()
        );

        System.out.println(
                "Equipo: " +
                rental.getEquipment().getName()
        );

        System.out.println(
                "Estado: " +
                rental.getStatus()
        );

        System.out.println(
                "Total: $" +
                rental.calculateTotal().amount()
        );

        rental.returnEquipment();

        System.out.println(
                "Estado final: " +
                rental.getStatus()
        );
    }
}