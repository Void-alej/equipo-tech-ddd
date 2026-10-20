package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    void save(Product product);

    Optional<Product> findById(UUID id);

    List<Product> findAll();

    List<Product> findBySellerId(UUID sellerId);
}
