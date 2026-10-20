package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.CartStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Cart {

    private final UUID id;
    private final UUID buyerId;
    private final List<CartItem> items;
    private CartStatus status;

    public Cart(UUID id, UUID buyerId) {
        if (id == null || buyerId == null) {
            throw new IllegalArgumentException(
                    "El identificador del carrito y el comprador son obligatorios.");
        }

        this.id = id;
        this.buyerId = buyerId;
        this.items = new ArrayList<>();
        this.status = CartStatus.ACTIVE;
    }

    public void addItem(
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            int quantity) {

        ensureActive();

        CartItem existingItem = items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            if (existingItem.getUnitPrice().compareTo(unitPrice) != 0
                    || !existingItem.getProductName().equals(productName)) {
                throw new IllegalArgumentException(
                        "Los datos del producto no coinciden con los del carrito.");
            }
            existingItem.increaseQuantity(quantity);
            return;
        }

        items.add(new CartItem(productId, productName, unitPrice, quantity));
    }

    public void removeItem(UUID productId) {
        ensureActive();

        boolean removed = items.removeIf(
                item -> item.getProductId().equals(productId));

        if (!removed) {
            throw new IllegalArgumentException(
                    "El producto no existe en el carrito.");
        }
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void convert() {
        ensureActive();

        if (items.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede convertir un carrito vacío en pedido.");
        }

        status = CartStatus.CONVERTED;
    }

    public void cancel() {
        ensureActive();
        status = CartStatus.CANCELLED;
    }

    private void ensureActive() {
        if (status != CartStatus.ACTIVE) {
            throw new IllegalStateException(
                    "El carrito ya no está activo y no puede modificarse.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public CartStatus getStatus() {
        return status;
    }
}
