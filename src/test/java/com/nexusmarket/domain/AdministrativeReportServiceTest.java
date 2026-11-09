package com.nexusmarket.domain;

import com.nexusmarket.application.dto.AdministrativeReport;
import com.nexusmarket.application.services.AdministrativeReportService;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.enums.InventoryMovementType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.models.InventoryMovement;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.Invoice;
import com.nexusmarket.domain.models.InvoiceItem;
import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.models.ShipmentItem;
import com.nexusmarket.domain.models.ReturnRequest;
import com.nexusmarket.domain.ports.out.*;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AdministrativeReportServiceTest {

    private InMemoryUserRepository userRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryWarehouseRepository warehouseRepository;
    private InMemoryInventoryRepository inventoryRepository;
    private InMemoryInventoryMovementRepository movementRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryInvoiceRepository invoiceRepository;
    private InMemoryShipmentRepository shipmentRepository;
    private InMemoryReturnRepository returnRepository;

    private AdministrativeReportService reportService;

    private User administrator;
    private User supervisor;
    private User buyer;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        productRepository = new InMemoryProductRepository();
        warehouseRepository = new InMemoryWarehouseRepository();
        inventoryRepository = new InMemoryInventoryRepository();
        movementRepository = new InMemoryInventoryMovementRepository();
        orderRepository = new InMemoryOrderRepository();
        invoiceRepository = new InMemoryInvoiceRepository();
        shipmentRepository = new InMemoryShipmentRepository();
        returnRepository = new InMemoryReturnRepository();

        administrator = createUser(
                "Administrador",
                "admin@example.com",
                "ADMIN-1001",
                UserRole.ADMINISTRATOR
        );

        supervisor = createUser(
                "Supervisor",
                "supervisor@example.com",
                "SUP-1001",
                UserRole.SUPERVISOR
        );

        buyer = createUser(
                "Comprador",
                "buyer@example.com",
                "BUYER-1001",
                UserRole.BUYER
        );

        userRepository.save(administrator);
        userRepository.save(supervisor);
        userRepository.save(buyer);

        reportService = new AdministrativeReportService(
                userRepository,
                productRepository,
                warehouseRepository,
                inventoryRepository,
                movementRepository,
                orderRepository,
                invoiceRepository,
                shipmentRepository,
                returnRepository
        );
    }

    @Test
    void shouldGenerateReportForAdministrator() {
        AdministrativeReport report =
                reportService.generateReport(administrator);

        assertNotNull(report);
        assertNotNull(report.generatedAt());

        assertEquals(3, report.users().size());

        assertTrue(report.products().isEmpty());
        assertTrue(report.warehouses().isEmpty());
        assertTrue(report.inventories().isEmpty());
        assertTrue(report.inventoryMovements().isEmpty());
        assertTrue(report.orders().isEmpty());
        assertTrue(report.invoices().isEmpty());
        assertTrue(report.shipments().isEmpty());
        assertTrue(report.returnRequests().isEmpty());
    }

    @Test
    void shouldGenerateReportForSupervisor() {
        AdministrativeReport report =
                reportService.generateReport(supervisor);

        assertNotNull(report);
        assertNotNull(report.generatedAt());
        assertEquals(3, report.users().size());
    }

    @Test
    void shouldRejectUsersWithoutReportPermission() {
        User seller = createUser(
                "Vendedor",
                "seller@example.com",
                "SELLER-1001",
                UserRole.SELLER
        );

        User logisticsOperator = createUser(
                "Operador logístico",
                "logistics@example.com",
                "LOG-1001",
                UserRole.LOGISTICS_OPERATOR
        );

        userRepository.save(seller);
        userRepository.save(logisticsOperator);

        List<User> unauthorizedUsers =
                List.of(buyer, seller, logisticsOperator);

        for (User user : unauthorizedUsers) {
            assertThrows(
                    UnauthorizedUserOperationException.class,
                    () -> reportService.generateReport(user)
            );
        }
    }

    @Test
    void shouldRejectInactiveAdministrator() {
        administrator.suspend();
        userRepository.save(administrator);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> reportService.generateReport(administrator)
        );
    }

    @Test
    void shouldRejectUnregisteredAdministrator() {
        User unregisteredAdministrator = createUser(
                "Administrador no registrado",
                "unregistered@example.com",
                "ADMIN-9999",
                UserRole.ADMINISTRATOR
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> reportService.generateReport(unregisteredAdministrator)
        );
    }

    @Test
    void shouldExposeImmutableReportCollections() {
        AdministrativeReport report =
                reportService.generateReport(administrator);

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.users().clear()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.products().clear()
        );
    }


    @Test
    void shouldConsolidateRecordsFromAllRepositories() {
        User seller = createUser(
                "Vendedor de prueba",
                "seller@example.com",
                "SELLER-1001",
                UserRole.SELLER
        );
        userRepository.save(seller);

        UUID productId = UUID.randomUUID();
        Product product = new Product(
                productId,
                seller.getId(),
                "Producto de prueba",
                "Descripción del producto",
                ProductType.PHYSICAL,
                new BigDecimal("125.00")
        );
        productRepository.save(product);

        Warehouse warehouse = new Warehouse(
                UUID.randomUUID(),
                "Bodega principal",
                "Dirección de prueba",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(warehouse);

        Inventory inventory = new Inventory(
                UUID.randomUUID(),
                productId,
                warehouse.getId(),
                10
        );
        inventoryRepository.save(inventory);

        movementRepository.save(new InventoryMovement(
                UUID.randomUUID(),
                inventory.getId(),
                InventoryMovementType.INCOME,
                10,
                administrator.getId(),
                "Ingreso inicial de prueba"
        ));

        Order order = new Order(
                UUID.randomUUID(),
                buyer.getId(),
                List.of(new OrderItem(
                        productId,
                        "Producto de prueba",
                        new BigDecimal("125.00"),
                        1
                ))
        );
        orderRepository.save(order);

        invoiceRepository.save(new Invoice(
                UUID.randomUUID(),
                order.getId(),
                buyer.getId(),
                buyer.getFullName(),
                buyer.getEmail().getValue(),
                buyer.getDocumentId().getValue(),
                List.of(new InvoiceItem(
                        productId,
                        "Producto de prueba",
                        new BigDecimal("125.00"),
                        1
                )),
                LocalDateTime.now()
        ));

        shipmentRepository.save(new Shipment(
                UUID.randomUUID(),
                order.getId(),
                warehouse.getId(),
                List.of(new ShipmentItem(
                        productId,
                        "Producto de prueba",
                        1
                ))
        ));

        returnRepository.save(new ReturnRequest(
                UUID.randomUUID(),
                order.getId(),
                buyer.getId(),
                productId,
                "Producto de prueba",
                1,
                new BigDecimal("125.00"),
                "Prueba de consolidación"
        ));

        AdministrativeReport report =
                reportService.generateReport(administrator);

        assertEquals(4, report.users().size());
        assertEquals(1, report.products().size());
        assertEquals(1, report.warehouses().size());
        assertEquals(1, report.inventories().size());
        assertEquals(1, report.inventoryMovements().size());
        assertEquals(1, report.orders().size());
        assertEquals(1, report.invoices().size());
        assertEquals(1, report.shipments().size());
        assertEquals(1, report.returnRequests().size());
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
