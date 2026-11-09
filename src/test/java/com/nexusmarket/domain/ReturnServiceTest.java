package com.nexusmarket.domain;

import com.nexusmarket.application.services.InventoryService;
import com.nexusmarket.application.services.ReturnService;
import com.nexusmarket.domain.enums.*;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.*;
import com.nexusmarket.domain.ports.out.*;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReturnServiceTest {

    private static class FailingReturnRepository
            extends InMemoryReturnRepository {

        private boolean failNextSave;

        void simulateNextSaveFailure() {
            failNextSave = true;
        }

        @Override
        public ReturnRequest save(ReturnRequest request) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException(
                        "Fallo simulado al guardar la devolución.");
            }
            return super.save(request);
        }
    }

    private UserRepository userRepository;
    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private InventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private WarehouseRepository warehouseRepository;
    private FailingReturnRepository returnRepository;

    private InventoryService inventoryService;
    private ReturnService returnService;

    private User buyer;
    private User anotherBuyer;
    private User seller;
    private User admin;
    private User logistics;

    private Product physicalProduct;
    private Product digitalProduct;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        productRepository = new InMemoryProductRepository();
        orderRepository = new InMemoryOrderRepository();
        inventoryRepository = new InMemoryInventoryRepository();
        movementRepository = new InMemoryInventoryMovementRepository();
        warehouseRepository = new InMemoryWarehouseRepository();
        returnRepository = new FailingReturnRepository();

        buyer = createUser("Comprador", "buyer@return.com",
                "CC61001", UserRole.BUYER);
        anotherBuyer = createUser("Otro comprador", "buyer2@return.com",
                "CC61002", UserRole.BUYER);
        seller = createUser("Vendedor", "seller@return.com",
                "NIT61001", UserRole.SELLER);
        admin = createUser("Administrador", "admin@return.com",
                "CC61003", UserRole.ADMINISTRATOR);
        logistics = createUser("Logística", "logistics@return.com",
                "CC61004", UserRole.LOGISTICS_OPERATOR);

        userRepository.save(buyer);
        userRepository.save(anotherBuyer);
        userRepository.save(seller);
        userRepository.save(admin);
        userRepository.save(logistics);

        physicalProduct = createProduct(
                "Teclado",
                ProductType.PHYSICAL,
                "125000"
        );

        digitalProduct = createProduct(
                "Licencia digital",
                ProductType.DIGITAL,
                "80000"
        );

        warehouse = new Warehouse(
                UUID.randomUUID(),
                "Bodega central",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );
        warehouseRepository.save(warehouse);

        inventoryService = new InventoryService(
                inventoryRepository,
                movementRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );

        returnService = new ReturnService(
                returnRepository,
                orderRepository,
                userRepository,
                productRepository,
                warehouseRepository,
                inventoryService
        );
    }

    @Test
    void shouldRequestPartialReturnForDeliveredOrder() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Producto defectuoso");

        assertEquals(ReturnStatus.REQUESTED, request.getStatus());
        assertEquals(1, request.getQuantity());
        assertEquals(new BigDecimal("125000"), request.getRefundAmount());

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }

    @Test
    void shouldAllowReturnForFinalizedOrderWithoutChangingIt() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, true);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "No cumple las expectativas");

        assertEquals(ReturnStatus.REQUESTED, request.getStatus());
        assertEquals(OrderStatus.FINALIZED, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }

    @Test
    void shouldRejectReturnQuantityAboveRemainingPurchasedQuantity() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Primera solicitud");

        assertThrows(IllegalArgumentException.class,
                () -> returnService.requestReturn(
                        buyer, order.getId(), physicalProduct.getId(),
                        2, "Segunda solicitud"));
    }

    @Test
    void shouldRestoreReturnQuantityAllowanceAfterRejection() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest firstRequest = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                2, "Primera solicitud");

        returnService.rejectReturn(admin, firstRequest.getId());

        ReturnRequest secondRequest = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                2, "Nueva solicitud");

        assertEquals(ReturnStatus.REJECTED, firstRequest.getStatus());
        assertEquals(ReturnStatus.REQUESTED, secondRequest.getStatus());
    }

    @Test
    void shouldRejectReturnRequestFromAnotherBuyer() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        assertThrows(UnauthorizedUserOperationException.class,
                () -> returnService.requestReturn(
                        anotherBuyer, order.getId(),
                        physicalProduct.getId(), 1, "Solicitud ajena"));
    }

    @Test
    void shouldReceiveSellableProductAndRegisterRefund() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Devolución de prueba");

        returnService.approveReturn(admin, request.getId());

        returnService.registerPhysicalReceipt(
                logistics,
                request.getId(),
                warehouse.getId(),
                ReturnCondition.SELLABLE
        );

        Inventory inventory = getInventory();

        assertEquals(1, inventory.getQuantity());
        assertEquals(ReturnStatus.REFUND_PENDING, request.getStatus());
        assertEquals(ReturnCondition.SELLABLE, request.getCondition());

        returnService.markRefunded(
                admin, request.getId(), "REF-RETURN-001");

        assertEquals(ReturnStatus.REFUNDED, request.getStatus());
        assertEquals("REF-RETURN-001", request.getRefundReference());

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(PaymentStatus.APPROVED, order.getPaymentStatus());
    }

    @Test
    void shouldNotAddDamagedProductToAvailableInventory() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Producto dañado");

        returnService.approveReturn(admin, request.getId());

        returnService.registerPhysicalReceipt(
                logistics,
                request.getId(),
                warehouse.getId(),
                ReturnCondition.DAMAGED
        );

        assertEquals(ReturnCondition.DAMAGED, request.getCondition());
        assertEquals(ReturnStatus.REFUND_PENDING, request.getStatus());

        assertTrue(
                inventoryRepository.findByProductAndWarehouse(
                        physicalProduct.getId(), warehouse.getId()).isEmpty()
        );

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void shouldRejectPhysicalReceiptAfterReturnWasRejected() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Solicitud que será rechazada");

        returnService.rejectReturn(admin, request.getId());

        assertThrows(IllegalStateException.class,
                () -> returnService.registerPhysicalReceipt(
                        logistics,
                        request.getId(),
                        warehouse.getId(),
                        ReturnCondition.SELLABLE));

        assertTrue(
                inventoryRepository.findByProductAndWarehouse(
                        physicalProduct.getId(), warehouse.getId()).isEmpty()
        );
    }

    @Test
    void shouldProcessDigitalReturnWithoutPhysicalInventory() {
        Order order = createDeliveredOrder(
                buyer, digitalProduct, 1, true);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), digitalProduct.getId(),
                1, "Problema con la licencia");

        returnService.approveReturn(admin, request.getId());

        returnService.approveDigitalReturnForRefund(
                admin, request.getId());

        assertEquals(ReturnStatus.REFUND_PENDING, request.getStatus());

        returnService.markRefunded(
                admin, request.getId(), "REF-DIGITAL-001");

        assertEquals(ReturnStatus.REFUNDED, request.getStatus());
        assertTrue(inventoryRepository.findAll().isEmpty());
        assertEquals(OrderStatus.FINALIZED, order.getStatus());
    }

    @Test
    void shouldRollbackInventoryWhenReturnPersistenceFails() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Prueba de compensación");

        returnService.approveReturn(admin, request.getId());

        returnRepository.simulateNextSaveFailure();

        assertThrows(IllegalStateException.class,
                () -> returnService.registerPhysicalReceipt(
                        logistics,
                        request.getId(),
                        warehouse.getId(),
                        ReturnCondition.SELLABLE));

        assertEquals(ReturnStatus.APPROVED, request.getStatus());

        Inventory inventory = getInventory();
        assertEquals(0, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
    }

    @Test
    void shouldRejectSellerAttemptToApproveReturn() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Prueba de autorización");

        assertThrows(UnauthorizedUserOperationException.class,
                () -> returnService.approveReturn(
                        seller, request.getId()));

        assertEquals(ReturnStatus.REQUESTED, request.getStatus());
    }

    @Test
    void shouldPreventBuyerFromMarkingRefundAsCompleted() {
        Order order = createDeliveredOrder(
                buyer, physicalProduct, 2, false);

        ReturnRequest request = returnService.requestReturn(
                buyer, order.getId(), physicalProduct.getId(),
                1, "Prueba de reembolso");

        returnService.approveReturn(admin, request.getId());

        returnService.registerPhysicalReceipt(
                logistics,
                request.getId(),
                warehouse.getId(),
                ReturnCondition.DAMAGED
        );

        assertThrows(UnauthorizedUserOperationException.class,
                () -> returnService.markRefunded(
                        buyer, request.getId(), "REF-UNAUTHORIZED"));

        assertEquals(ReturnStatus.REFUND_PENDING, request.getStatus());
    }

    private Product createProduct(
            String name, ProductType type, String price) {

        Product product = new Product(
                UUID.randomUUID(),
                seller.getId(),
                name,
                "Producto para pruebas de devolución",
                type,
                new BigDecimal(price)
        );

        product.publish();
        productRepository.save(product);
        return product;
    }

    private Order createDeliveredOrder(
            User purchaser,
            Product selectedProduct,
            int quantity,
            boolean finalized) {

        Order order = new Order(
                UUID.randomUUID(),
                purchaser.getId(),
                List.of(new OrderItem(
                        selectedProduct.getId(),
                        selectedProduct.getName(),
                        selectedProduct.getPrice(),
                        quantity
                ))
        );

        order.registerPayment(true);
        order.dispatch();
        order.markDelivered();

        if (finalized) {
            order.finalizeOrder();
        }

        orderRepository.save(order);
        return order;
    }

    private Inventory getInventory() {
        return inventoryRepository.findByProductAndWarehouse(
                physicalProduct.getId(),
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
