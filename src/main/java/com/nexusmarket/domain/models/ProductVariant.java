package com.nexusmarket.domain.models;

import java.util.Objects;
import java.util.UUID;

public class ProductVariant {

    private final UUID id;
    private final String description;

    public ProductVariant(UUID id, String description) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "El identificador de la variante es obligatorio."
            );
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "La descripción de la variante es obligatoria."
            );
        }

        this.id = id;
        this.description = description.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ProductVariant variant)) return false;
        return id.equals(variant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
