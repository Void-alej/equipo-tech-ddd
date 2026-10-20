package com.nexusmarket.domain;

import com.nexusmarket.application.services.CartService;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryProductRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartServiceTest {

    private UserRepository userRepository;
    private ProductRepository productRepository;
    private CartService cartService;

    private User buyer;
    private User otherBuyer;
    private User seller;
    private Product product;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        productRepository = new InMemoryProductRepository();

        cartService = new CartService(userRepository, productRepository);

        buyer = createUser("Comprador", "buyer@example.com",
                "CC30001", UserRole.BUYER);
        otherBuyer = createUser("Otro comprador", "buyer2@example.com",
                "CC30002", UserRole.BUYER);
        seller = createUser("Vendedor", "seller@example.com",
                "NIT30001", UserRole.SELLER);

        userRepository.save(buyer);
        userRepository.save(otherBuyer);
        userRepository.save(seller);

        product = new Product(
                UUID.randomUUID(),
                seller.getId(),
                "Teclado",
                "Teclado mecánico",
                ProductType.PHYSICAL,
                new BigDecimal("120000")
        );

        product.publish();
        productRepository.save(product);
    }

    @Test
    void shouldCreateCartForRegisteredBuyer() {
        Cart cart = cartService.createCart(buyer);

        assertEquals(buyer.getId(), cart.getBuyerId());
        assertTrue(cart.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, cart.getTotal());
    }

    @Test
    void shouldAddPublishedProductUsingCatalogPrice() {
        Cart cart = cartService.createCart(buyer);

        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        assertEquals(new BigDecimal("240000"), cart.getTotal());
        assertEquals("Teclado", cart.getItems().get(0).getProductName());
        assertEquals(new BigDecimal("120000"),
                cart.getItems().get(0).getUnitPrice());
    }

    @Test
    void shouldRejectNonBuyer() {
        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> cartService.createCart(seller)
        );
    }

    @Test
    void shouldRejectAnotherBuyersCart() {
        Cart cart = cartService.createCart(buyer);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> cartService.getCart(otherBuyer, cart.getId())
        );
    }

    @Test
    void shouldRejectUnpublishedProduct() {
        Product unpublished = new Product(
                UUID.randomUUID(),
                seller.getId(),
                "Mouse",
                "Mouse inalámbrico",
                ProductType.PHYSICAL,
                new BigDecimal("50000")
        );

        productRepository.save(unpublished);
        Cart cart = cartService.createCart(buyer);

        assertThrows(
                IllegalStateException.class,
                () -> cartService.addProduct(
                        buyer, cart.getId(), unpublished.getId(), 1)
        );

        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void shouldRejectInvalidQuantity() {
        Cart cart = cartService.createCart(buyer);

        assertThrows(
                IllegalArgumentException.class,
                () -> cartService.addProduct(
                        buyer, cart.getId(), product.getId(), 0)
        );

        assertTrue(cart.getItems().isEmpty());
    }

    private User createUser(
            String name,
            String email,
            String document,
            UserRole role) {

        return new User(
                UUID.randomUUID(),
                name,
                new Email(email),
                new DocumentId(document),
                role
        );
    }
}
