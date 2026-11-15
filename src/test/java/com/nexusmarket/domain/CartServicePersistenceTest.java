package com.nexusmarket.domain;

import com.nexusmarket.application.services.CartService;
import com.nexusmarket.domain.enums.BuyerCommercialStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.models.BuyerProfile;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.infrastructure.repositories.InMemoryBuyerProfileRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryProductRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CartServicePersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRestoreCartItemsAndStatusWhenServiceIsRecreated() {
        String previousDirectory =
                System.getProperty("nexusmarket.data.dir");

        try {
            System.setProperty(
                    "nexusmarket.data.dir", temporaryDirectory.toString());

            var users = new InMemoryUserRepository();
            var products = new InMemoryProductRepository();
            var profiles = new InMemoryBuyerProfileRepository();

            User buyer = new User(
                    UUID.randomUUID(),
                    "Comprador de persistencia",
                    new Email("persistent-buyer@example.com"),
                    new DocumentId("CC908765"),
                    UserRole.BUYER);
            users.save(buyer);
            profiles.save(new BuyerProfile(
                    buyer.getId(), "Dirección principal", List.of(),
                    BuyerCommercialStatus.ACTIVE));

            Product product = new Product(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Producto físico",
                    "Producto para prueba de persistencia",
                    ProductType.PHYSICAL,
                    new BigDecimal("45000"));
            product.publish();
            products.save(product);

            CartService firstService = new CartService(users, products, profiles);
            Cart cart = firstService.createCart(buyer);
            firstService.addProduct(buyer, cart.getId(), product.getId(), 3);

            CartService restartedService = new CartService(users, products, profiles);
            Cart restored = restartedService.getCart(buyer, cart.getId());

            assertEquals(1, restored.getItems().size());
            assertEquals(product.getId(), restored.getItems().get(0).getProductId());
            assertEquals(3, restored.getItems().get(0).getQuantity());
            assertEquals(new BigDecimal("135000"), restored.getTotal());
        } finally {
            if (previousDirectory == null) {
                System.clearProperty("nexusmarket.data.dir");
            } else {
                System.setProperty("nexusmarket.data.dir", previousDirectory);
            }
        }
    }
}
