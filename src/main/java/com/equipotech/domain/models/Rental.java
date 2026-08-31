package com.equipotech.domain.models;

import com.equipotech.domain.enums.RentalStatus;
import com.equipotech.domain.exceptions.InvalidRentalException;
import com.equipotech.domain.valueobjects.Money;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class Rental {

    private final UUID id;
    private final Customer customer;
    private final Equipment equipment;
    private final LocalDate startDate;
    private final LocalDate endDate;

    private RentalStatus status;

    public Rental(
            Customer customer,
            Equipment equipment,
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (customer == null) {
            throw new InvalidRentalException(
                    "El cliente es obligatorio"
            );
        }

        if (equipment == null) {
            throw new InvalidRentalException(
                    "El equipo es obligatorio"
            );
        }

        if (startDate == null || endDate == null) {
            throw new InvalidRentalException(
                    "Las fechas son obligatorias"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new InvalidRentalException(
                    "La fecha final no puede ser anterior a la inicial"
            );
        }

        this.id = UUID.randomUUID();
        this.customer = customer;
        this.equipment = equipment;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = RentalStatus.CREATED;
    }

    public void confirm() {

        equipment.reserve();

        status = RentalStatus.CONFIRMED;
    }

    public void activate() {

        if (status != RentalStatus.CONFIRMED) {

            throw new InvalidRentalException(
                    "El alquiler debe estar confirmado"
            );
        }

        equipment.rent();

        status = RentalStatus.ACTIVE;
    }

    public void returnEquipment() {

        if (status != RentalStatus.ACTIVE) {

            throw new InvalidRentalException(
                    "El alquiler no está activo"
            );
        }

        equipment.returnToStock();

        status = RentalStatus.RETURNED;
    }

    public Money calculateTotal() {

        long days = ChronoUnit.DAYS.between(
                startDate,
                endDate
        ) + 1;

        return equipment
                .getDailyRate()
                .multiply(days);
    }

    public UUID getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public RentalStatus getStatus() {
        return status;
    }
}