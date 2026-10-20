package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.ports.out.OrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryOrderRepository implements OrderRepository {

    private final ConcurrentMap<UUID, Order> orders = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("El pedido es obligatorio.");
        }
        orders.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }

    @Override
    public List<Order> findByBuyerId(UUID buyerId) {
        if (buyerId == null) {
            throw new IllegalArgumentException(
                    "El identificador del comprador es obligatorio.");
        }

        return orders.values().stream()
                .filter(order -> order.getBuyerId().equals(buyerId))
                .toList();
    }
}
