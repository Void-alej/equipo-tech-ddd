package com.nexusmarket.infrastructure.http;

import com.nexusmarket.application.services.AdministrativeReportService;
import com.nexusmarket.application.services.AuthenticationService;
import com.nexusmarket.application.services.BuyerService;
import com.nexusmarket.application.services.CartService;
import com.nexusmarket.application.services.InventoryService;
import com.nexusmarket.application.services.InvoiceService;
import com.nexusmarket.application.services.OrderService;
import com.nexusmarket.application.services.ProductService;
import com.nexusmarket.application.services.ReturnService;
import com.nexusmarket.application.services.SellerOnboardingService;
import com.nexusmarket.application.services.ShipmentService;
import com.nexusmarket.application.services.UserService;
import com.nexusmarket.application.services.WarehouseService;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.*;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.*;

import java.nio.file.Path;
import java.util.UUID;

/** Composition root del servidor ejecutable de NexusMarket. */
public final class NexusMarketRuntime {

    public final UserRepository users;
    public final ProductRepository products;
    public final WarehouseRepository warehouses;
    public final InventoryRepository inventories;
    public final InventoryMovementRepository movements;
    public final BuyerProfileRepository buyerProfiles;
    public final OrderRepository orders;
    public final OrderAllocationRepository allocations;
    public final ShipmentRepository shipments;
    public final ReturnRepository returns;
    public final InvoiceRepository invoices;

    public final UserService userService;
    public final BuyerService buyerService;
    public final WarehouseService warehouseService;
    public final ProductService productService;
    public final InventoryService inventoryService;
    public final CartService cartService;
    public final ShipmentService shipmentService;
    public final OrderService orderService;
    public final ReturnService returnService;
    public final InvoiceService invoiceService;
    public final AdministrativeReportService administrativeReportService;
    public final SellerOnboardingService sellerOnboardingService;
    public final AuthenticationService authenticationService;

    private final User logisticsOperator;

    public NexusMarketRuntime() {
        String directory = System.getenv().getOrDefault(
                "NEXUSMARKET_DATA_DIR", "data/nexusmarket");
        System.setProperty("nexusmarket.data.dir",
                Path.of(directory).toAbsolutePath().toString());

        // Las instancias persistentes se construyen solo después de configurar
        // la ruta de almacenamiento.
        users = new InMemoryUserRepository();
        products = new InMemoryProductRepository();
        warehouses = new InMemoryWarehouseRepository();
        inventories = new InMemoryInventoryRepository();
        movements = new InMemoryInventoryMovementRepository();
        buyerProfiles = new InMemoryBuyerProfileRepository();
        orders = new InMemoryOrderRepository();
        allocations = new InMemoryOrderAllocationRepository();
        shipments = new InMemoryShipmentRepository();
        returns = new InMemoryReturnRepository();
        invoices = new InMemoryInvoiceRepository();

        logisticsOperator = findOrCreateLogisticsOperator();

        userService = new UserService(users);
        buyerService = new BuyerService(users, buyerProfiles);
        warehouseService = new WarehouseService(warehouses, users);
        productService = new ProductService(products);
        inventoryService = new InventoryService(
                inventories, movements, products, warehouses, users);
        cartService = new CartService(users, products, buyerProfiles);
        shipmentService = new ShipmentService(
                shipments, allocations, orders, products, warehouses, users);
        orderService = new OrderService(
                cartService, orders, users, inventoryService, inventories,
                warehouses, products, logisticsOperator, allocations, shipments);
        returnService = new ReturnService(
                returns, orders, users, products, warehouses, inventoryService);
        invoiceService = new InvoiceService(invoices, orders, users);
        administrativeReportService = new AdministrativeReportService(
                users, products, warehouses, inventories, movements,
                orders, invoices, shipments, returns);
        sellerOnboardingService = new SellerOnboardingService(
                userService, warehouseService);
        authenticationService = new AuthenticationService(users);
    }

    private User findOrCreateLogisticsOperator() {
        return users.findAll().stream()
                .filter(user -> user.getRole() == UserRole.LOGISTICS_OPERATOR
                        && user.isActive())
                .findFirst()
                .orElseGet(() -> {
                    User operator = new User(
                            UUID.randomUUID(),
                            "Operador logístico de demostración",
                            new Email("logistica@nexusmarket.local"),
                            new DocumentId("900000002"),
                            UserRole.LOGISTICS_OPERATOR);
                    users.save(operator);
                    return operator;
                });
    }

    public void initializeDemoAccounts() {
        User administrator = users.findAll().stream()
                .filter(user -> user.getEmail().getValue()
                        .equalsIgnoreCase("admin@nexusmarket.local"))
                .findFirst()
                .orElseGet(() -> {
                    User created = new User(
                            UUID.randomUUID(),
                            "Administrador NexusMarket",
                            new Email("admin@nexusmarket.local"),
                            new DocumentId("900000001"),
                            UserRole.ADMINISTRATOR);
                    users.save(created);
                    return created;
                });

        String adminPassword = System.getenv().getOrDefault(
                "NEXUSMARKET_ADMIN_PASSWORD", "Admin123!");
        authenticationService.ensurePassword(administrator, adminPassword);

        String logisticsPassword = System.getenv().getOrDefault(
                "NEXUSMARKET_LOGISTICS_PASSWORD", "Logistica123!");
        authenticationService.ensurePassword(logisticsOperator, logisticsPassword);

        if (warehouses.findAll().isEmpty()) {
            warehouseService.createMarketplaceWarehouse(
                    administrator, "Bodega central", "Medellín, Colombia");
        }
    }

    public User getLogisticsOperator() {
        return users.findById(logisticsOperator.getId())
                .orElse(logisticsOperator);
    }
}
