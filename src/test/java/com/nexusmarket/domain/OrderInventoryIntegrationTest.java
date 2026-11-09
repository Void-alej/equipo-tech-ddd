
package com.nexusmarket.domain;

import com.nexusmarket.application.services.CartService;
import com.nexusmarket.application.services.InventoryService;
import com.nexusmarket.application.services.OrderService;
import com.nexusmarket.application.services.ShipmentService;
import com.nexusmarket.domain.enums.BuyerCommercialStatus;
import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.models.BuyerProfile;
import com.nexusmarket.domain.enums.PaymentStatus;
import com.nexusmarket.domain.enums.ShipmentStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.models.OrderAllocation;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.*;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderInventoryIntegrationTest {

    private UserRepository userRepository;
    private ProductRepository productRepository;
    private BuyerProfileRepository buyerProfileRepository;
    private OrderRepository orderRepository;
    private InventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private WarehouseRepository warehouseRepository;
    private OrderAllocationRepository orderAllocationRepository;
    private ShipmentRepository shipmentRepository;

    private FailingOrderRepository failingOrderRepository;

    private CartService cartService;
    private InventoryService inventoryService;
    private OrderService orderService;

    private User buyer;
    private User seller;
    private User logistics;
    private Product product;
    private Warehouse warehouse;

    private static class FailingOrderRepository
            extends InMemoryOrderRepository {

        private boolean failNextSave;

        void simulateNextSaveFailure() {
            failNextSave = true;
        }

        @Override
        public void save(Order order) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException(
                        "Fallo simulado al guardar el pedido."
                );
            }

            super.save(order);
        }
    }

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        productRepository = new InMemoryProductRepository();
        buyerProfileRepository = new InMemoryBuyerProfileRepository();

        failingOrderRepository = new FailingOrderRepository();
        orderRepository = failingOrderRepository;

        inventoryRepository = new InMemoryInventoryRepository();
        movementRepository = new InMemoryInventoryMovementRepository();
        warehouseRepository = new InMemoryWarehouseRepository();
        orderAllocationRepository = new InMemoryOrderAllocationRepository();
        shipmentRepository = new InMemoryShipmentRepository();

        buyer = createUser("Comprador", "buyer@test.com",
                "CC51001", UserRole.BUYER);
        seller = createUser("Vendedor", "seller@test.com",
                "NIT51001", UserRole.SELLER);
        logistics = createUser("Logística", "logistics@test.com",
                "CC51002", UserRole.LOGISTICS_OPERATOR);

        userRepository.save(buyer);
        userRepository.save(seller);
        userRepository.save(logistics);

        buyerProfileRepository.save(new BuyerProfile(
                buyer.getId(), "Dirección principal", List.of(),
                BuyerCommercialStatus.ACTIVE));

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

        warehouse = new Warehouse(
                UUID.randomUUID(),
                "Bodega central",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(warehouse);

        cartService = new CartService(
                userRepository, productRepository, buyerProfileRepository);

        inventoryService = new InventoryService(
                inventoryRepository,
                movementRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        orderService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                inventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                logistics,
                orderAllocationRepository,
                shipmentRepository
        );

        inventoryService.addStock(
                logistics,
                product.getId(),
                warehouse.getId(),
                10,
                "Carga inicial de inventario"
        );
    }

    @Test
    void shouldReserveInventoryWhenCreatingOrder() {
        Order order = createOrder();

        Inventory inventory = getInventory();

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(10, inventory.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());
    }

    @Test
    void shouldRegisterSaleWhenPaymentIsApproved() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), true);

        Inventory inventory = getInventory();

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(8, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());
    }

    @Test
    void shouldReleaseReservationWhenPaymentIsRejected() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), false);

        Inventory inventory = getInventory();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REJECTED, order.getPaymentStatus());
        assertEquals(10, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(10, inventory.getAvailableQuantity());
    }

    @Test
    void shouldReleaseReservationWhenPendingOrderIsCancelled() {
        Order order = createOrder();

        orderService.cancelOrder(buyer, order.getId());

        Inventory inventory = getInventory();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(10, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(10, inventory.getAvailableQuantity());
    }

    @Test
    void shouldRestoreFirstReservationWhenSecondReleaseFails() {
        Warehouse secondWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Segunda bodega",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(secondWarehouse);

        inventoryService.addStock(
                logistics, product.getId(), secondWarehouse.getId(),
                1, "Carga para prueba"
        );

        inventoryService.adjustStock(
                logistics, product.getId(), warehouse.getId(),
                1, "Preparación de prueba"
        );

        List<UUID> attemptedReleases = new ArrayList<>();

        InventoryService failingInventoryService =
                new InventoryService(
                        inventoryRepository,
                        movementRepository,
                        productRepository,
                        warehouseRepository,
                        userRepository
                ) {
                    private int releaseCalls = 0;

                    @Override
                    public Inventory releaseReservation(
                            User actor,
                            UUID productId,
                            UUID warehouseId,
                            int quantity,
                            String description) {

                        attemptedReleases.add(warehouseId);
                        releaseCalls++;

                        if (releaseCalls == 2) {
                            throw new IllegalStateException(
                                    "Fallo simulado en la segunda liberación."
                            );
                        }

                        return super.releaseReservation(
                                actor, productId, warehouseId,
                                quantity, description
                        );
                    }
                };

        orderService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                failingInventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                logistics,
                orderAllocationRepository,
                shipmentRepository
        );

        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        Order order = orderService.createOrderFromCart(buyer, cart.getId());

        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelOrder(buyer, order.getId())
        );

        assertEquals(2, attemptedReleases.size());

        for (UUID warehouseId : attemptedReleases) {
            Inventory inventory = inventoryRepository
                    .findByProductAndWarehouse(product.getId(), warehouseId)
                    .orElseThrow();

            assertEquals(1, inventory.getQuantity());
            assertEquals(1, inventory.getReservedQuantity());
        }

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void shouldRestoreFirstReservationWhenSecondReleaseFailsDuringPaymentRejection() {
        Warehouse secondWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Segunda bodega",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(secondWarehouse);

        inventoryService.addStock(
                logistics, product.getId(), secondWarehouse.getId(),
                1, "Carga para prueba"
        );

        inventoryService.adjustStock(
                logistics, product.getId(), warehouse.getId(),
                1, "Preparación de prueba"
        );

        List<UUID> attemptedReleases = new ArrayList<>();

        InventoryService failingInventoryService =
                new InventoryService(
                        inventoryRepository,
                        movementRepository,
                        productRepository,
                        warehouseRepository,
                        userRepository
                ) {
                    private int releaseCalls = 0;

                    @Override
                    public Inventory releaseReservation(
                            User actor,
                            UUID productId,
                            UUID warehouseId,
                            int quantity,
                            String description) {

                        attemptedReleases.add(warehouseId);
                        releaseCalls++;

                        if (releaseCalls == 2) {
                            throw new IllegalStateException(
                                    "Fallo simulado en la segunda liberación."
                            );
                        }

                        return super.releaseReservation(
                                actor, productId, warehouseId,
                                quantity, description
                        );
                    }
                };

        orderService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                failingInventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                logistics,
                orderAllocationRepository,
                shipmentRepository
        );

        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        Order order = orderService.createOrderFromCart(buyer, cart.getId());

        assertThrows(
                IllegalStateException.class,
                () -> orderService.registerPayment(buyer, order.getId(), false)
        );

        assertEquals(2, attemptedReleases.size());

        for (UUID warehouseId : attemptedReleases) {
            Inventory inventory = inventoryRepository
                    .findByProductAndWarehouse(product.getId(), warehouseId)
                    .orElseThrow();

            assertEquals(1, inventory.getQuantity());
            assertEquals(1, inventory.getReservedQuantity());
        }

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void shouldRestoreInventoryWhenPaidOrderIsCancelled() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), true);
        orderService.cancelOrder(buyer, order.getId());

        Inventory inventory = getInventory();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(10, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(10, inventory.getAvailableQuantity());
    }

    @Test
    void shouldRollbackFirstReturnWhenSecondReturnFails() {
        Warehouse secondWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Segunda bodega",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(secondWarehouse);

        inventoryService.addStock(
                logistics, product.getId(), secondWarehouse.getId(),
                1, "Carga para prueba"
        );

        inventoryService.adjustStock(
                logistics, product.getId(), warehouse.getId(),
                1, "Preparación de prueba"
        );

        List<UUID> attemptedReturns = new ArrayList<>();

        InventoryService failingInventoryService =
                new InventoryService(
                        inventoryRepository,
                        movementRepository,
                        productRepository,
                        warehouseRepository,
                        userRepository
                ) {
                    private int returnCalls = 0;

                    @Override
                    public Inventory registerReturn(
                            User actor,
                            UUID productId,
                            UUID warehouseId,
                            int quantity,
                            String description) {

                        attemptedReturns.add(warehouseId);
                        returnCalls++;

                        if (returnCalls == 2) {
                            throw new IllegalStateException(
                                    "Fallo simulado en la segunda devolución."
                            );
                        }

                        return super.registerReturn(
                                actor, productId, warehouseId,
                                quantity, description
                        );
                    }
                };

        orderService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                failingInventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                logistics,
                orderAllocationRepository,
                shipmentRepository
        );

        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        Order order = orderService.createOrderFromCart(buyer, cart.getId());
        orderService.registerPayment(buyer, order.getId(), true);

        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelOrder(buyer, order.getId())
        );

        assertEquals(2, attemptedReturns.size());

        for (UUID warehouseId : attemptedReturns) {
            Inventory inventory = inventoryRepository
                    .findByProductAndWarehouse(product.getId(), warehouseId)
                    .orElseThrow();

            assertEquals(0, inventory.getQuantity());
            assertEquals(0, inventory.getReservedQuantity());
        }

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }

    @Test
    void shouldRollbackFirstSaleWhenSecondWarehouseSaleFails() {
        inventoryService.adjustStock(
                logistics,
                product.getId(),
                warehouse.getId(),
                1,
                "Preparación de prueba"
        );

        Warehouse secondWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Segunda bodega",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(secondWarehouse);

        inventoryService.addStock(
                logistics,
                product.getId(),
                secondWarehouse.getId(),
                1,
                "Carga para prueba"
        );

        List<UUID> attemptedSales = new ArrayList<>();

        InventoryService failingInventoryService =
                new InventoryService(
                        inventoryRepository,
                        movementRepository,
                        productRepository,
                        warehouseRepository,
                        userRepository
                ) {
                    private int saleCalls = 0;

                    @Override
                    public Inventory registerSale(
                            User actor,
                            UUID productId,
                            UUID warehouseId,
                            int quantity,
                            String description) {

                        attemptedSales.add(warehouseId);
                        saleCalls++;

                        if (saleCalls == 2) {
                            throw new IllegalStateException(
                                    "Fallo simulado en la segunda venta."
                            );
                        }

                        return super.registerSale(
                                actor, productId, warehouseId,
                                quantity, description
                        );
                    }
                };

        orderService = new OrderService(
                cartService,
                orderRepository,
                userRepository,
                failingInventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                logistics,
                orderAllocationRepository,
                shipmentRepository
        );

        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        Order order = orderService.createOrderFromCart(
                buyer,
                cart.getId()
        );

        assertThrows(
                IllegalStateException.class,
                () -> orderService.registerPayment(
                        buyer,
                        order.getId(),
                        true
                )
        );

        assertEquals(2, attemptedSales.size());

        for (UUID warehouseId : attemptedSales) {
            Inventory inventory = inventoryRepository
                    .findByProductAndWarehouse(
                            product.getId(),
                            warehouseId
                    )
                    .orElseThrow();

            assertEquals(1, inventory.getQuantity());
            assertEquals(1, inventory.getReservedQuantity());
        }

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    // PRUEBAS NUEVAS: fallos al guardar el pedido.

    @Test
    void shouldRestoreInventoryWhenApprovedPaymentSaveFails() {
        Order order = createOrder();

        failingOrderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> orderService.registerPayment(
                        buyer, order.getId(), true
                )
        );

        Inventory inventory = getInventory();

        assertEquals(10, inventory.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void shouldRestoreReservationWhenRejectedPaymentSaveFails() {
        Order order = createOrder();

        failingOrderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> orderService.registerPayment(
                        buyer, order.getId(), false
                )
        );

        Inventory inventory = getInventory();

        assertEquals(10, inventory.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void shouldRestoreReservationWhenPendingOrderCancellationSaveFails() {
        Order order = createOrder();

        failingOrderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelOrder(buyer, order.getId())
        );

        Inventory inventory = getInventory();

        assertEquals(10, inventory.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(PaymentStatus.PENDING, order.getPaymentStatus());
    }

    @Test
    void shouldReverseReturnWhenPaidOrderCancellationSaveFails() {
        Order order = createOrder();

        orderService.registerPayment(buyer, order.getId(), true);

        failingOrderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelOrder(buyer, order.getId())
        );

        Inventory inventory = getInventory();

        assertEquals(8, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(8, inventory.getAvailableQuantity());

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }


    @Test
    void shouldPersistAllocationWhenCreatingOrder() {
        Order order = createOrder();

        List<OrderAllocation> allocations =
                orderAllocationRepository.findByOrderId(order.getId());

        assertEquals(1, allocations.size());

        OrderAllocation allocation = allocations.get(0);

        assertEquals(order.getId(), allocation.getOrderId());
        assertEquals(product.getId(), allocation.getProductId());
        assertEquals(warehouse.getId(), allocation.getWarehouseId());
        assertEquals(2, allocation.getQuantity());

        // La asignación debe seguir disponible después de confirmar el pago.
        orderService.registerPayment(buyer, order.getId(), true);

        assertEquals(1,
                orderAllocationRepository.findByOrderId(order.getId()).size());
    }

    @Test
    void shouldPersistSeparateAllocationsForDifferentWarehouses() {
        Warehouse secondWarehouse = new Warehouse(
                UUID.randomUUID(),
                "Segunda bodega",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(secondWarehouse);

        inventoryService.addStock(
                logistics,
                product.getId(),
                secondWarehouse.getId(),
                1,
                "Carga para distribución"
        );

        inventoryService.adjustStock(
                logistics,
                product.getId(),
                warehouse.getId(),
                1,
                "Ajuste para distribución"
        );

        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        Order order = orderService.createOrderFromCart(buyer, cart.getId());

        List<OrderAllocation> allocations =
                orderAllocationRepository.findByOrderId(order.getId());

        assertEquals(2, allocations.size());

        assertEquals(
                2,
                allocations.stream()
                        .mapToInt(OrderAllocation::getQuantity)
                        .sum()
        );

        assertEquals(
                2,
                allocations.stream()
                        .map(OrderAllocation::getWarehouseId)
                        .distinct()
                        .count()
        );
    }

    @Test
    void shouldRemoveAllocationsAndReleaseInventoryWhenOrderSaveFails() {
        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);

        failingOrderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrderFromCart(buyer, cart.getId())
        );

        assertTrue(orderAllocationRepository.findAll().isEmpty());

        Inventory inventory = getInventory();

        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(10, inventory.getAvailableQuantity());

        assertEquals(
                com.nexusmarket.domain.enums.CartStatus.ACTIVE,
                cart.getStatus()
        );
    }


    @Test
    void shouldPreventDirectDispatchWhenShipmentWorkflowIsEnabled() {
        Order order = createOrder();
        orderService.registerPayment(buyer, order.getId(), true);

        assertThrows(
                IllegalStateException.class,
                () -> orderService.dispatchOrder(logistics, order.getId())
        );

        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void shouldPreventDirectDeliveryWhenShipmentWorkflowIsEnabled() {
        Order order = createOrder();
        orderService.registerPayment(buyer, order.getId(), true);

        assertThrows(
                IllegalStateException.class,
                () -> orderService.markOrderDelivered(
                        logistics, order.getId())
        );

        assertEquals(OrderStatus.PAID, order.getStatus());
    }


    @Test
    void shouldCompleteEndToEndFlowFromCartToDeliveredShipment() {
        // 1. Crear pedido y reservar inventario.
        Order order = createOrder();

        Inventory beforePayment = getInventory();

        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(10, beforePayment.getQuantity());
        assertEquals(2, beforePayment.getReservedQuantity());
        assertEquals(8, beforePayment.getAvailableQuantity());

        // 2. Aprobar el pago y registrar la venta.
        orderService.registerPayment(buyer, order.getId(), true);

        Inventory afterPayment = getInventory();

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(8, afterPayment.getQuantity());
        assertEquals(0, afterPayment.getReservedQuantity());
        assertEquals(8, afterPayment.getAvailableQuantity());

        // 3. Comprobar las asignaciones persistidas.
        List<OrderAllocation> allocations =
                orderAllocationRepository.findByOrderId(order.getId());

        assertEquals(1, allocations.size());
        assertEquals(product.getId(), allocations.get(0).getProductId());
        assertEquals(warehouse.getId(), allocations.get(0).getWarehouseId());
        assertEquals(2, allocations.get(0).getQuantity());

        // 4. Crear los envíos a partir de las asignaciones.
        ShipmentService shipmentService = new ShipmentService(
                shipmentRepository,
                orderAllocationRepository,
                orderRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        List<Shipment> shipments =
                shipmentService.createShipments(logistics, order.getId());

        assertEquals(1, shipments.size());

        Shipment shipment = shipments.get(0);

        assertEquals(warehouse.getId(), shipment.getWarehouseId());
        assertEquals(ShipmentStatus.PREPARING, shipment.getStatus());
        assertEquals(2, shipment.getItems().stream()
                .mapToInt(item -> item.getQuantity())
                .sum());

        // 5. Preparar, empacar y despachar.
        shipmentService.packShipment(
                logistics, shipment.getId(), "Pedido empacado.");

        assertEquals(ShipmentStatus.PACKED, shipment.getStatus());

        shipmentService.dispatchShipment(
                logistics, shipment.getId(), "Pedido despachado.");

        assertEquals(ShipmentStatus.DISPATCHED, shipment.getStatus());
        assertEquals(OrderStatus.DISPATCHED, order.getStatus());

        // 6. Registrar transporte y entrega.
        shipmentService.markShipmentInTransit(
                logistics, shipment.getId(), "Pedido en transporte.");

        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.getStatus());

        shipmentService.deliverShipment(
                logistics, shipment.getId(), "Pedido entregado al comprador.");

        assertEquals(ShipmentStatus.DELIVERED, shipment.getStatus());
        assertEquals(OrderStatus.DELIVERED, order.getStatus());

        // 7. El inventario no debe descontarse una segunda vez.
        Inventory afterDelivery = getInventory();

        assertEquals(8, afterDelivery.getQuantity());
        assertEquals(0, afterDelivery.getReservedQuantity());
        assertEquals(8, afterDelivery.getAvailableQuantity());

        // 8. El comprador finaliza el pedido.
        orderService.finalizeOrder(buyer, order.getId());

        assertEquals(OrderStatus.FINALIZED, order.getStatus());

        // El historial de asignaciones se conserva.
        assertEquals(
                1,
                orderAllocationRepository.findByOrderId(order.getId()).size()
        );

        assertEquals(4, shipment.getTrackingHistory().size());
    }


    @Test
    void shouldPreventPaidOrderCancellationWhenShipmentExists() {
        Order order = createOrder();
        orderService.registerPayment(buyer, order.getId(), true);

        ShipmentService shipmentService = new ShipmentService(
                shipmentRepository,
                orderAllocationRepository,
                orderRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        List<Shipment> shipments =
                shipmentService.createShipments(logistics, order.getId());

        assertEquals(1, shipments.size());
        assertEquals(OrderStatus.PAID, order.getStatus());

        assertThrows(
                IllegalStateException.class,
                () -> orderService.cancelOrder(buyer, order.getId())
        );

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
        assertEquals(8, getInventory().getQuantity());
        assertEquals(0, getInventory().getReservedQuantity());
        assertEquals(ShipmentStatus.PREPARING, shipments.get(0).getStatus());
    }


    @Test
    void shouldSerializeConcurrentPaymentAndCancellation() throws Exception {
        Order order = createOrder();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var paymentFuture = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    orderService.registerPayment(
                            buyer, order.getId(), true);
                } catch (IllegalStateException expected) {
                    // La cancelación pudo ganar la carrera.
                }

                return null;
            });

            var cancellationFuture = executor.submit(() -> {
                ready.countDown();
                start.await();

                orderService.cancelOrder(buyer, order.getId());
                return null;
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            paymentFuture.get(10, TimeUnit.SECONDS);
            cancellationFuture.get(10, TimeUnit.SECONDS);

        } finally {
            executor.shutdownNow();
        }

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        assertTrue(
                order.getPaymentStatus() == PaymentStatus.REJECTED
                || order.getPaymentStatus() == PaymentStatus.REFUNDED
        );

        assertEquals(10, getInventory().getQuantity());
        assertEquals(0, getInventory().getReservedQuantity());
        assertEquals(10, getInventory().getAvailableQuantity());
    }

    private Order createOrder() {
        Cart cart = cartService.createCart(buyer);
        cartService.addProduct(buyer, cart.getId(), product.getId(), 2);
        return orderService.createOrderFromCart(buyer, cart.getId());
    }

    private Inventory getInventory() {
        return inventoryRepository.findByProductAndWarehouse(
                product.getId(),
                warehouse.getId()
        ).orElseThrow();
    }

    private User createUser(
            String name, String email, String document, UserRole role) {
        return new User(
                UUID.randomUUID(),
                name,
                new Email(email),
                new DocumentId(document),
                role
        );
    }
}
