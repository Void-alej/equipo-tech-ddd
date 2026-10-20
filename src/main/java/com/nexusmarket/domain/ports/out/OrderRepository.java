package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(UUID id);
    List<Order> findAll();
    List<Order> findByBuyerId(UUID buyerId);
}
