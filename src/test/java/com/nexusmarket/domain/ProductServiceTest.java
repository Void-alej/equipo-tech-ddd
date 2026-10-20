package com.nexusmarket.domain;

import com.nexusmarket.application.services.ProductService;
import com.nexusmarket.domain.enums.ProductStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceTest {

    private InMemoryProductRepository repository;
    private ProductService productService;
    private User seller;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductRepository();
        productService = new ProductService(repository);
        seller = new User(
                UUID.randomUUID(),
                "Vendedor de prueba",
                new Email("seller@example.com"),
                new DocumentId("NIT12345"),
                UserRole.SELLER
        );
    }

    @Test
    void shouldCreateProductInitiallySuspended() {
        Product product = productService.createProduct(
                seller,
                "Teclado",
                "Teclado mecánico",
                ProductType.PHYSICAL,
                new BigDecimal("120000")
        );

        assertEquals(ProductStatus.SUSPENDED, product.getStatus());
        assertEquals(seller.getId(), product.getSellerId());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldPublishProduct() {
        Product product = createSampleProduct();

        productService.publishProduct(seller, product.getId());

        assertEquals(ProductStatus.PUBLISHED, product.getStatus());
        assertTrue(product.isAvailableForSale());
        assertEquals(1, productService.getPublishedProducts().size());
    }

    @Test
    void shouldNotAllowBuyerToCreateProduct() {
        User buyer = new User(
                UUID.randomUUID(),
                "Comprador",
                new Email("buyer@example.com"),
                new DocumentId("CC12345"),
                UserRole.BUYER
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> productService.createProduct(
                        buyer,
                        "Teclado",
                        "Teclado mecánico",
                        ProductType.PHYSICAL,
                        new BigDecimal("120000")
                )
        );
    }

    @Test
    void shouldNotAllowSellerToModifyAnotherSellersProduct() {
        Product product = createSampleProduct();

        User otherSeller = new User(
                UUID.randomUUID(),
                "Otro vendedor",
                new Email("other@example.com"),
                new DocumentId("NIT54321"),
                UserRole.SELLER
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> productService.publishProduct(otherSeller, product.getId())
        );
    }

    @Test
    void shouldRejectZeroPrice() {
        assertThrows(
                IllegalArgumentException.class,
                () -> productService.createProduct(
                        seller,
                        "Teclado",
                        "Teclado mecánico",
                        ProductType.PHYSICAL,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void shouldNotRepublishDiscontinuedProduct() {
        Product product = createSampleProduct();
        productService.discontinueProduct(seller, product.getId());

        assertThrows(
                IllegalStateException.class,
                () -> productService.publishProduct(seller, product.getId())
        );
    }

    private Product createSampleProduct() {
        return productService.createProduct(
                seller,
                "Teclado",
                "Teclado mecánico",
                ProductType.PHYSICAL,
                new BigDecimal("120000")
        );
    }
}
