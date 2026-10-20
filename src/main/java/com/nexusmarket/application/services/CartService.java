package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.CartStatus;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.CartItem;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CartService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final Map<UUID, Cart> carts = new ConcurrentHashMap<>();

    public CartService(
            UserRepository userRepository,
            ProductRepository productRepository) {

        if (userRepository == null || productRepository == null) {
            throw new IllegalArgumentException(
                    "Los repositorios de usuarios y productos son obligatorios.");
        }

        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public Cart createCart(User actor) {
        requireActiveBuyer(actor);

        Cart cart = new Cart(UUID.randomUUID(), actor.getId());
        carts.put(cart.getId(), cart);
        return cart;
    }

    public Cart addProduct(
            User actor,
            UUID cartId,
            UUID productId,
            int quantity) {

        Cart cart = findCartOrThrow(cartId);
        authorizeCartOwner(actor, cart);
        ensureActiveCart(cart);

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto solicitado."));

        if (!product.isAvailableForSale()) {
            throw new IllegalStateException(
                    "El producto no está publicado ni disponible para la venta.");
        }

        cart.addItem(
                product.getId(),
                product.getName(),
                product.getPrice(),
                quantity
        );

        return cart;
    }

    public Cart removeProduct(
            User actor,
            UUID cartId,
            UUID productId) {

        Cart cart = findCartOrThrow(cartId);
        authorizeCartOwner(actor, cart);
        ensureActiveCart(cart);

        cart.removeItem(productId);
        return cart;
    }

    public Cart getCart(User actor, UUID cartId) {
        Cart cart = findCartOrThrow(cartId);
        authorizeCartOwner(actor, cart);
        return cart;
    }

    public List<CartItem> getItems(User actor, UUID cartId) {
        return getCart(actor, cartId).getItems();
    }

    public Cart convertCart(User actor, UUID cartId) {
        Cart cart = findCartOrThrow(cartId);
        authorizeCartOwner(actor, cart);
        ensureActiveCart(cart);

        cart.convert();
        return cart;
    }

    public Cart cancelCart(User actor, UUID cartId) {
        Cart cart = findCartOrThrow(cartId);
        authorizeCartOwner(actor, cart);
        ensureActiveCart(cart);

        cart.cancel();
        return cart;
    }

    private void requireActiveBuyer(User actor) {
        if (actor == null || !actor.isActive()
                || actor.getRole() != UserRole.BUYER) {
            throw new UnauthorizedUserOperationException(
                    "Solo un comprador activo puede utilizar el carrito.");
        }

        User registeredUser = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedUserOperationException(
                        "El comprador no está registrado."));

        if (!registeredUser.isActive()
                || registeredUser.getRole() != UserRole.BUYER) {
            throw new UnauthorizedUserOperationException(
                    "El usuario registrado no puede utilizar el carrito.");
        }
    }

    private void authorizeCartOwner(User actor, Cart cart) {
        requireActiveBuyer(actor);

        if (!cart.getBuyerId().equals(actor.getId())) {
            throw new UnauthorizedUserOperationException(
                    "No puedes acceder al carrito de otro comprador.");
        }
    }

    private void ensureActiveCart(Cart cart) {
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new IllegalStateException(
                    "El carrito ya no está activo.");
        }
    }

    private Cart findCartOrThrow(UUID cartId) {
        if (cartId == null) {
            throw new IllegalArgumentException(
                    "El identificador del carrito es obligatorio.");
        }

        Cart cart = carts.get(cartId);

        if (cart == null) {
            throw new IllegalArgumentException(
                    "No existe el carrito solicitado.");
        }

        return cart;
    }
}
