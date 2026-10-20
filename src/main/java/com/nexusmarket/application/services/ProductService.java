package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        if (productRepository == null) {
            throw new IllegalArgumentException("El repositorio de productos es obligatorio.");
        }
        this.productRepository = productRepository;
    }

    public Product createProduct(
            User actor,
            String name,
            String description,
            ProductType type,
            BigDecimal price
    ) {
        if (actor == null || !actor.isActive()
                || actor.getRole() != UserRole.SELLER) {
            throw new UnauthorizedUserOperationException(
                    "Solo un vendedor activo puede registrar productos."
            );
        }

        Product product = new Product(
                UUID.randomUUID(),
                actor.getId(),
                name,
                description,
                type,
                price
        );

        productRepository.save(product);
        return product;
    }

    public Product publishProduct(User actor, UUID productId) {
        Product product = findProductOrThrow(productId);
        authorizeSeller(actor, product);

        product.publish();
        productRepository.save(product);
        return product;
    }

    public Product suspendProduct(User actor, UUID productId) {
        Product product = findProductOrThrow(productId);
        authorizeSeller(actor, product);

        product.suspend();
        productRepository.save(product);
        return product;
    }

    public Product discontinueProduct(User actor, UUID productId) {
        Product product = findProductOrThrow(productId);
        authorizeSeller(actor, product);

        product.discontinue();
        productRepository.save(product);
        return product;
    }

    public List<Product> getPublishedProducts() {
        return productRepository.findAll().stream()
                .filter(Product::isAvailableForSale)
                .toList();
    }

    private Product findProductOrThrow(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("El identificador del producto es obligatorio.");
        }

        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto solicitado."
                ));
    }

    private void authorizeSeller(User actor, Product product) {
        if (actor == null || !actor.isActive()
                || actor.getRole() != UserRole.SELLER
                || !actor.getId().equals(product.getSellerId())) {
            throw new UnauthorizedUserOperationException(
                    "Solo el vendedor propietario puede modificar este producto."
            );
        }
    }
}
