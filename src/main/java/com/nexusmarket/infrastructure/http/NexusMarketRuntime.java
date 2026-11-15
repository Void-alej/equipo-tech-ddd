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

    public final UserRepository users = new InMemoryUserRepository();
    public final ProductRepository products = new InMemoryProductRepository();
    public final WarehouseRepository warehouses = new InMemoryWarehouseRepository();
    public final InventoryRepository inventories = new InMemoryInventoryRepository();
    public final InventoryMovementRepository movements =
            new InMemoryInventoryMovementRepository();
    public final BuyerProfileRepository buyerProfiles =
            new InMemoryBuyerProfileRepository();
    public final OrderRepository orders = new InMemoryOrderRepository();
    public final OrderAllocationRepository allocations =
            new InMemoryOrderAllocationRepository();
    public final ShipmentRepository shipments = new InMemoryShipmentRepository();
    public final ReturnRepository returns = new InMemoryReturnRepository();
    public final InvoiceRepository invoices = new InMemoryInvoiceRepository();

    public final UserService userService = new UserService(users);
    public final BuyerService buyerService = new BuyerService(users, buyerProfiles);
    public final WarehouseService warehouseService =
            new WarehouseService(warehouses, users);
    public final ProductService productService = new ProductService(products);
    public final InventoryService inventoryService = new InventoryService(
            inventories, movements, products, warehouses, users);
    public final CartService cartService =
            new CartService(users, products, buyerProfiles);
    public final ShipmentService shipmentService = new ShipmentService(
            shipments, allocations, orders, products, warehouses, users);
    public final OrderService orderService = new OrderService(
            cartService, orders, users, inventoryService, inventories,
            warehouses, products, requireLogisticsOperator(),
            allocations, shipments);
    public final ReturnService returnService = new ReturnService(
            returns, orders, users, products, warehouses, inventoryService);
    public final InvoiceService invoiceService =
            new InvoiceService(invoices, orders, users);
    public final AdministrativeReportService administrativeReportService =
            new AdministrativeReportService(users, products, warehouses,
                    inventories, movements, orders, invoices, shipments, returns);
    public final SellerOnboardingService sellerOnboardingService =
            new SellerOnboardingService(userService, warehouseService);
    public final AuthenticationService authenticationService =
            new AuthenticationService(users);

    private final User logisticsOperator;

    public NexusMarketRuntime() {
        String directory = System.getenv().getOrDefault(
                "NEXUSMARKET_DATA_DIR", "data/nexusmarket");
        System.setProperty("nexusmarket.data.dir",
                Path.of(directory).toAbsolutePath().toString());

        // Nota: los repositorios anteriores se inicializan antes del constructor.
        // Se crea una aplicación nueva para que todos lean la ruta configurada.
        throw new IllegalStateException("Use NexusMarketRuntime.create() to initialize storage.");
    }

    private NexusMarketRuntime(boolean initialized) {
        logisticsOperator = requireLogisticsOperator();
    }

    private User requireLogisticsOperator() {
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

    public static NexusMarketRuntime create() {
        // Configurar la ruta antes de inicializar los repositorios.
        String directory = System.getenv().getOrDefault(
                "NEXUSMARKET_DATA_DIR", "data/nexusmarket");
        System.setProperty("nexusmarket.data.dir",
                Path.of(directory).toAbsolutePath().toString());

        return new NexusMarketRuntime(true);
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
        return users.findById(logisticsOperator.getId()).orElse(logisticsOperator);
    }
}
