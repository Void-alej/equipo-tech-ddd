package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.WarehouseType;

import java.util.UUID;

public class Warehouse {

    private final UUID id;
    private final String name;
    private final String address;
    private final WarehouseType type;
    private final UUID sellerId;
    private boolean active;

    public Warehouse(
            UUID id,
            String name,
            String address,
            WarehouseType type,
            UUID sellerId
    ) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador de la bodega es obligatorio.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la bodega es obligatorio.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("La dirección de la bodega es obligatoria.");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de bodega es obligatorio.");
        }
        if (type == WarehouseType.SELLER && sellerId == null) {
            throw new IllegalArgumentException(
                    "Una bodega de vendedor debe tener un propietario."
            );
        }
        if (type == WarehouseType.MARKETPLACE && sellerId != null) {
            throw new IllegalArgumentException(
                    "Una bodega del marketplace no puede tener un vendedor propietario."
            );
        }

        this.id = id;
        this.name = name.trim();
        this.address = address.trim();
        this.type = type;
        this.sellerId = sellerId;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public WarehouseType getType() {
        return type;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }
}
