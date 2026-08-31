package com.equipotech.domain.models;

import com.equipotech.domain.enums.EquipmentStatus;
import com.equipotech.domain.exceptions.EquipmentUnavailableException;
import com.equipotech.domain.valueobjects.Money;

import java.util.UUID;

public class Equipment {

    private final UUID id;
    private final String name;
    private final String category;
    private final Money dailyRate;

    private EquipmentStatus status;

    public Equipment(
            String name,
            String category,
            Money dailyRate
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del equipo es obligatorio"
            );
        }

        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException(
                    "La categoría es obligatoria"
            );
        }

        this.id = UUID.randomUUID();
        this.name = name;
        this.category = category;
        this.dailyRate = dailyRate;
        this.status = EquipmentStatus.AVAILABLE;
    }

    public void reserve() {

        if (status != EquipmentStatus.AVAILABLE) {

            throw new EquipmentUnavailableException(
                    "El equipo no está disponible"
            );
        }

        status = EquipmentStatus.RESERVED;
    }

    public void rent() {

        if (status != EquipmentStatus.RESERVED) {

            throw new EquipmentUnavailableException(
                    "El equipo debe estar reservado"
            );
        }

        status = EquipmentStatus.RENTED;
    }

    public void returnToStock() {

        if (status != EquipmentStatus.RENTED) {

            throw new EquipmentUnavailableException(
                    "El equipo no está alquilado"
            );
        }

        status = EquipmentStatus.AVAILABLE;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public Money getDailyRate() {
        return dailyRate;
    }

    public EquipmentStatus getStatus() {
        return status;
    }
}