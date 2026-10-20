package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.ProductStatus;
import com.nexusmarket.domain.enums.ProductType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class Product {

    private final UUID id;
    private final UUID sellerId;
    private final String name;
    private final String description;
    private final ProductType type;
    private final BigDecimal price;
    private ProductStatus status;

    public Product(
            UUID id,
            UUID sellerId,
            String name,
            String description,
            ProductType type,
            BigDecimal price
    ) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador del producto es obligatorio.");
        }
        if (sellerId == null) {
            throw new IllegalArgumentException("El vendedor es obligatorio.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("La descripción del producto es obligatoria.");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de producto es obligatorio.");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que cero.");
        }

        this.id = id;
        this.sellerId = sellerId;
        this.name = name.trim();
        this.description = description.trim();
        this.type = type;
        this.price = price;
        this.status = ProductStatus.SUSPENDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductType getType() {
        return type;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void publish() {
        if (status == ProductStatus.DISCONTINUED) {
            throw new IllegalStateException(
                    "No se puede publicar un producto descontinuado."
            );
        }
        status = ProductStatus.PUBLISHED;
    }

    public void suspend() {
        if (status == ProductStatus.DISCONTINUED) {
            throw new IllegalStateException(
                    "Un producto descontinuado no se puede suspender."
            );
        }
        status = ProductStatus.SUSPENDED;
    }

    public void discontinue() {
        status = ProductStatus.DISCONTINUED;
    }

    public boolean isAvailableForSale() {
        return status == ProductStatus.PUBLISHED;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Product product)) return false;
        return id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
