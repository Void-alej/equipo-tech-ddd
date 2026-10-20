package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.ports.out.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryProductRepository implements ProductRepository {

    private final ConcurrentMap<UUID, Product> products = new ConcurrentHashMap<>();

    @Override
    public void save(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("El producto no puede ser nulo.");
        }
        products.put(product.getId(), product);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(products.values());
    }

    @Override
    public List<Product> findBySellerId(UUID sellerId) {
        return products.values().stream()
                .filter(product -> product.getSellerId().equals(sellerId))
                .toList();
    }
}
