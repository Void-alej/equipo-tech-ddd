package com.nexusmarket.domain;

import com.nexusmarket.application.services.InventoryService;
import com.nexusmarket.domain.enums.InventoryMovementType;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.models.InventoryMovement;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.InventoryMovementRepository;
import com.nexusmarket.domain.ports.out.InventoryRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryInventoryMovementRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryInventoryRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryProductRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryWarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private WarehouseRepository warehouseRepository;
    private InventoryService inventoryService;

    private User administrator;
    private User seller;
    private User otherSeller;
    private Product product;
    private Warehouse marketplaceWarehouse;
    private Warehouse otherSellerWarehouse;

    @BeforeEach
    void setUp() {
        inventoryRepository = new InMemoryInventoryRepository();
        movementRepository = new InMemoryInventoryMovementRepository();
        productRepository = new InMemoryProductRepository();
        userRepository = new InMemoryUserRepository();
        warehouseRepository = new InMemoryWarehouseRepository();

        inventoryService = new InventoryService(
                inventoryRepository,
                movementRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        administrator = createUser(
                "Administrador",
                "admin@example.com",
                "CC10001",
                UserRole.ADMINISTRATOR
        );

        seller = createUser(
                "Vendedor",
                "seller@example.com",
                "NIT10001",
                UserRole.SELLER
        );

        otherSeller = createUser(
                "Otro vendedor",
                "other@example.com",
                "NIT20002",
                UserRole.SELLER
        );

        userRepository.save(administrator);
        userRepository.save(seller);
        userRepository.save(otherSeller);

        product = new Product(
                UUID.randomUUID(),
                otherSeller.getId(),
                "Teclado",
                "Teclado mecánico",
                ProductType.PHYSICAL,
                new BigDecimal("120000")
        );
        productRepository.save(product);

        marketplaceWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Bodega central",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(marketplaceWarehouse);

        otherSellerWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Bodega del vendedor",
                "Envigado",
                WarehouseType.SELLER,
                otherSeller.getId()
        );
        warehouseRepository.save(otherSellerWarehouse);
    }

    @Test
    void shouldAddStockAndRegisterIncomeMovement() {
        Inventory inventory = inventoryService.addStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                10,
                "Ingreso inicial de mercancía"
        );

        assertEquals(10, inventory.getQuantity());
        assertEquals(10, inventory.getAvailableQuantity());

        assertEquals(1, movementRepository.findAll().size());

        InventoryMovement movement = movementRepository.findAll().get(0);
        assertEquals(InventoryMovementType.INCOME, movement.getType());
        assertEquals(10, movement.getQuantity());
        assertEquals(administrator.getId(), movement.getActorId());
    }

    @Test
    void shouldNotChangeStockWhenDescriptionIsBlank() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.addStock(
                        administrator,
                        product.getId(),
                        marketplaceWarehouse.getId(),
                        10,
                        "   "
                )
        );

        assertTrue(inventoryRepository.findAll().isEmpty());
        assertTrue(movementRepository.findAll().isEmpty());
    }

    @Test
    void shouldRejectReservationAboveAvailableStock() {
        inventoryService.addStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                5,
                "Ingreso de cinco unidades"
        );

        product.publish();
        productRepository.save(product);

        assertThrows(
                IllegalStateException.class,
                () -> inventoryService.reserveStock(
                        administrator,
                        product.getId(),
                        marketplaceWarehouse.getId(),
                        6,
                        "Reserva superior a las existencias"
                )
        );

        Inventory inventory = inventoryRepository
                .findByProductAndWarehouse(
                        product.getId(),
                        marketplaceWarehouse.getId()
                )
                .orElseThrow();

        assertEquals(5, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());

        // Solo debe existir el movimiento del ingreso inicial.
        assertEquals(1, movementRepository.findAll().size());
    }

    @Test
    void shouldNotAllowSellerToModifyAnotherSellersInventory() {
        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> inventoryService.addStock(
                        seller,
                        product.getId(),
                        otherSellerWarehouse.getId(),
                        5,
                        "Intento de modificar inventario ajeno"
                )
        );

        assertTrue(inventoryRepository.findAll().isEmpty());
        assertTrue(movementRepository.findAll().isEmpty());
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
