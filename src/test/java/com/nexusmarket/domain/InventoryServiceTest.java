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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test
    void shouldReuseExistingInventoryWhenAddingStock() {
        Inventory first = inventoryService.addStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                10,
                "Primer ingreso"
        );

        Inventory second = inventoryService.addStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                5,
                "Segundo ingreso"
        );

        assertEquals(first.getId(), second.getId());
        assertEquals(15, second.getQuantity());
        assertEquals(1, inventoryRepository.findAll().size());
        assertEquals(2, movementRepository.findAll().size());
    }

    @Test
    void shouldRejectDifferentInventoryIdsForSameProductAndWarehouse() {
        Inventory first = new Inventory(
                UUID.randomUUID(),
                product.getId(),
                marketplaceWarehouse.getId(),
                10
        );

        Inventory duplicate = new Inventory(
                UUID.randomUUID(),
                product.getId(),
                marketplaceWarehouse.getId(),
                5
        );

        inventoryRepository.save(first);

        assertThrows(
                IllegalStateException.class,
                () -> inventoryRepository.save(duplicate)
        );

        assertEquals(1, inventoryRepository.findAll().size());
    }


    @Test
    void shouldNotChangeInventoryWhenMovementSaveFails() {
        Inventory existing = new Inventory(
                UUID.randomUUID(),
                product.getId(),
                marketplaceWarehouse.getId(),
                10
        );
        inventoryRepository.save(existing);

        InMemoryInventoryMovementRepository failingMovementRepository =
                new InMemoryInventoryMovementRepository() {
                    @Override
                    public InventoryMovement save(InventoryMovement movement) {
                        throw new IllegalStateException(
                                "Fallo simulado al guardar el movimiento.");
                    }
                };

        InventoryService service = new InventoryService(
                inventoryRepository,
                failingMovementRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.addStock(
                        administrator,
                        product.getId(),
                        marketplaceWarehouse.getId(),
                        5,
                        "Ingreso de prueba"
                )
        );

        Inventory stored = inventoryRepository
                .findByProductAndWarehouse(
                        product.getId(),
                        marketplaceWarehouse.getId()
                )
                .orElseThrow();

        assertEquals(10, stored.getQuantity());
        assertEquals(0, stored.getReservedQuantity());
        assertTrue(failingMovementRepository.findAll().isEmpty());
    }

    @Test
    void shouldDeleteMovementWhenInventorySaveFails() {
        class FailingInventoryRepository extends InMemoryInventoryRepository {
            private boolean failOnSave;

            @Override
            public synchronized Inventory save(Inventory inventory) {
                if (failOnSave) {
                    throw new IllegalStateException(
                            "Fallo simulado al guardar el inventario.");
                }
                return super.save(inventory);
            }
        }

        FailingInventoryRepository failingInventoryRepository =
                new FailingInventoryRepository();

        Inventory existing = new Inventory(
                UUID.randomUUID(),
                product.getId(),
                marketplaceWarehouse.getId(),
                10
        );
        failingInventoryRepository.save(existing);

        failingInventoryRepository.failOnSave = true;

        InventoryService service = new InventoryService(
                failingInventoryRepository,
                movementRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.addStock(
                        administrator,
                        product.getId(),
                        marketplaceWarehouse.getId(),
                        5,
                        "Ingreso de prueba"
                )
        );

        Inventory stored = failingInventoryRepository
                .findByProductAndWarehouse(
                        product.getId(),
                        marketplaceWarehouse.getId()
                )
                .orElseThrow();

        assertEquals(10, stored.getQuantity());
        assertEquals(0, stored.getReservedQuantity());
        assertTrue(movementRepository.findAll().isEmpty());
    }


    @Test
    void shouldPreserveAllConcurrentStockAdditions() throws Exception {
        int operations = 20;

        ExecutorService executor = Executors.newFixedThreadPool(operations);
        CountDownLatch ready = new CountDownLatch(operations);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?>[] futures = new Future<?>[operations];

            for (int i = 0; i < operations; i++) {
                futures[i] = executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "No se inició la prueba concurrente.");
                    }

                    inventoryService.addStock(
                            administrator,
                            product.getId(),
                            marketplaceWarehouse.getId(),
                            1,
                            "Ingreso concurrente"
                    );

                    return null;
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Inventory stored = inventoryRepository
                .findByProductAndWarehouse(
                        product.getId(),
                        marketplaceWarehouse.getId()
                )
                .orElseThrow();

        assertEquals(operations, stored.getQuantity());
        assertEquals(operations, stored.getAvailableQuantity());
        assertEquals(operations, movementRepository.findAll().size());
    }

    @Test
    void shouldPreventConcurrentReservationsAboveAvailableStock()
            throws Exception {
        inventoryService.addStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                10,
                "Ingreso inicial"
        );

        product.publish();
        productRepository.save(product);

        int operations = 10;
        AtomicInteger successfulReservations = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(operations);
        CountDownLatch ready = new CountDownLatch(operations);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?>[] futures = new Future<?>[operations];

            for (int i = 0; i < operations; i++) {
                futures[i] = executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "No se inició la prueba concurrente.");
                    }

                    try {
                        inventoryService.reserveStock(
                                administrator,
                                product.getId(),
                                marketplaceWarehouse.getId(),
                                2,
                                "Reserva concurrente"
                        );

                        successfulReservations.incrementAndGet();
                    } catch (IllegalStateException expected) {
                        assertEquals(
                                "No hay suficientes unidades disponibles para reservar.",
                                expected.getMessage()
                        );
                    }

                    return null;
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Inventory stored = inventoryRepository
                .findByProductAndWarehouse(
                        product.getId(),
                        marketplaceWarehouse.getId()
                )
                .orElseThrow();

        assertEquals(5, successfulReservations.get());
        assertEquals(10, stored.getQuantity());
        assertEquals(10, stored.getReservedQuantity());
        assertEquals(0, stored.getAvailableQuantity());

        // Un ingreso inicial y cinco reservas exitosas.
        assertEquals(6, movementRepository.findAll().size());
    }

    @Test
    void shouldPreserveStockWhenRegisteringConcurrentSales()
            throws Exception {
        inventoryService.addStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                10,
                "Ingreso inicial"
        );

        product.publish();
        productRepository.save(product);

        inventoryService.reserveStock(
                administrator,
                product.getId(),
                marketplaceWarehouse.getId(),
                10,
                "Reserva inicial"
        );

        int operations = 10;
        ExecutorService executor = Executors.newFixedThreadPool(operations);
        CountDownLatch ready = new CountDownLatch(operations);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?>[] futures = new Future<?>[operations];

            for (int i = 0; i < operations; i++) {
                futures[i] = executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "No se inició la prueba concurrente.");
                    }

                    inventoryService.registerSale(
                            administrator,
                            product.getId(),
                            marketplaceWarehouse.getId(),
                            1,
                            "Venta concurrente"
                    );

                    return null;
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        Inventory stored = inventoryRepository
                .findByProductAndWarehouse(
                        product.getId(),
                        marketplaceWarehouse.getId()
                )
                .orElseThrow();

        assertEquals(0, stored.getQuantity());
        assertEquals(0, stored.getReservedQuantity());
        assertEquals(0, stored.getAvailableQuantity());

        var movements = movementRepository.findAll();

        assertEquals(12, movements.size());

        assertEquals(
                1,
                movements.stream()
                        .filter(m -> m.getType() == InventoryMovementType.INCOME)
                        .count()
        );

        assertEquals(
                1,
                movements.stream()
                        .filter(m -> m.getType() == InventoryMovementType.RESERVATION)
                        .count()
        );

        assertEquals(
                10,
                movements.stream()
                        .filter(m -> m.getType() == InventoryMovementType.SALE)
                        .count()
        );

        assertEquals(
                10,
                movements.stream()
                        .filter(m -> m.getType() == InventoryMovementType.SALE)
                        .mapToInt(InventoryMovement::getQuantity)
                        .sum()
        );
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
