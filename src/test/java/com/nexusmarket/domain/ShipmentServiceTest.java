package com.nexusmarket.domain;

import com.nexusmarket.application.services.ShipmentService;
import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.ShipmentStatus;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderAllocation;
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.infrastructure.repositories.InMemoryOrderAllocationRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryOrderRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryProductRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryShipmentRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryWarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentServiceTest {

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
                        "Fallo simulado al guardar el pedido.");
            }

            super.save(order);
        }
    }

    private static class FailingShipmentRepository
            extends InMemoryShipmentRepository {

        private boolean failNextSave;

        void simulateNextSaveFailure() {
            failNextSave = true;
        }

        @Override
        public synchronized void save(Shipment shipment) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException(
                        "Fallo simulado al guardar el envío.");
            }

            super.save(shipment);
        }
    }

    private InMemoryOrderAllocationRepository allocationRepository;
    private FailingOrderRepository orderRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryWarehouseRepository warehouseRepository;
    private InMemoryUserRepository userRepository;
    private InMemoryShipmentRepository shipmentRepository;

    private ShipmentService shipmentService;

    private User buyer;
    private User logistics;
    private User seller;

    private Product physicalProduct;
    private Warehouse warehouseA;
    private Warehouse warehouseB;
    private Order paidOrder;

    @BeforeEach
    void setUp() {
        allocationRepository = new InMemoryOrderAllocationRepository();
        orderRepository = new FailingOrderRepository();
        productRepository = new InMemoryProductRepository();
        warehouseRepository = new InMemoryWarehouseRepository();
        userRepository = new InMemoryUserRepository();
        shipmentRepository = new InMemoryShipmentRepository();

        buyer = createUser(
                "Comprador", "buyer@shipment.test",
                "BUYER-5101", UserRole.BUYER);

        logistics = createUser(
                "Logística", "logistics@shipment.test",
                "LOG-5101", UserRole.LOGISTICS_OPERATOR);

        seller = createUser(
                "Vendedor", "seller@shipment.test",
                "SELLER-5101", UserRole.SELLER);

        userRepository.save(buyer);
        userRepository.save(logistics);
        userRepository.save(seller);

        physicalProduct = new Product(
                UUID.randomUUID(),
                seller.getId(),
                "Teclado",
                "Teclado mecánico",
                ProductType.PHYSICAL,
                new BigDecimal("120000")
        );
        physicalProduct.publish();
        productRepository.save(physicalProduct);

        warehouseA = new Warehouse(
                UUID.randomUUID(),
                "Bodega A",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );

        warehouseB = new Warehouse(
                UUID.randomUUID(),
                "Bodega B",
                "Medellín",
                WarehouseType.MARKETPLACE,
                null
        );

        warehouseRepository.save(warehouseA);
        warehouseRepository.save(warehouseB);

        paidOrder = createPaidPhysicalOrder(3);

        allocationRepository.saveAll(List.of(
                allocation(paidOrder, warehouseA, 2),
                allocation(paidOrder, warehouseB, 1)
        ));

        shipmentService = createService(shipmentRepository);
    }

    @Test
    void shouldGenerateOneShipmentPerWarehouse() {
        List<Shipment> shipments =
                shipmentService.createShipments(logistics, paidOrder.getId());

        assertEquals(2, shipments.size());
        assertEquals(2,
                shipments.stream()
                        .map(Shipment::getWarehouseId)
                        .distinct()
                        .count());

        assertEquals(
                3,
                shipments.stream()
                        .flatMap(shipment -> shipment.getItems().stream())
                        .mapToInt(item -> item.getQuantity())
                        .sum()
        );

        assertTrue(shipments.stream().allMatch(
                shipment -> shipment.getStatus() == ShipmentStatus.PREPARING));
    }

    @Test
    void shouldRejectShipmentGenerationWhenPaymentIsPending() {
        Order pendingOrder = new Order(
                UUID.randomUUID(),
                buyer.getId(),
                List.of(new OrderItem(
                        physicalProduct.getId(),
                        physicalProduct.getName(),
                        new BigDecimal("120000"),
                        1
                ))
        );
        orderRepository.save(pendingOrder);

        allocationRepository.saveAll(List.of(
                allocation(pendingOrder, warehouseA, 1)
        ));

        assertThrows(
                IllegalStateException.class,
                () -> shipmentService.createShipments(
                        logistics, pendingOrder.getId())
        );

        assertTrue(shipmentRepository.findByOrderId(
                pendingOrder.getId()).isEmpty());
    }

    @Test
    void shouldRejectIncompletePhysicalAllocations() {
        Order incompleteOrder = createPaidPhysicalOrder(3);

        assertThrows(
                IllegalStateException.class,
                () -> shipmentService.createShipments(
                        logistics, incompleteOrder.getId())
        );

        assertTrue(shipmentRepository.findByOrderId(
                incompleteOrder.getId()).isEmpty());
    }

    @Test
    void shouldPreventDuplicateShipmentGeneration() {
        shipmentService.createShipments(logistics, paidOrder.getId());

        assertThrows(
                IllegalStateException.class,
                () -> shipmentService.createShipments(
                        logistics, paidOrder.getId())
        );

        assertEquals(
                2,
                shipmentRepository.findByOrderId(paidOrder.getId()).size()
        );
    }

    @Test
    void shouldUpdateOrderOnlyWhenAllShipmentsReachEachStage() {
        List<Shipment> shipments =
                shipmentService.createShipments(logistics, paidOrder.getId());

        shipments.forEach(shipment ->
                shipmentService.packShipment(
                        logistics, shipment.getId(), "Empaque verificado."));

        shipmentService.dispatchShipment(
                logistics, shipments.get(0).getId(), "Primer envío despachado.");

        assertEquals(OrderStatus.PAID, paidOrder.getStatus());

        shipmentService.dispatchShipment(
                logistics, shipments.get(1).getId(), "Segundo envío despachado.");

        assertEquals(OrderStatus.DISPATCHED, paidOrder.getStatus());

        for (Shipment shipment : shipments) {
            shipmentService.markShipmentInTransit(
                    logistics, shipment.getId(), "En transporte.");
        }

        shipmentService.deliverShipment(
                logistics, shipments.get(0).getId(), "Primer envío entregado.");

        assertEquals(OrderStatus.DISPATCHED, paidOrder.getStatus());

        shipmentService.deliverShipment(
                logistics, shipments.get(1).getId(), "Segundo envío entregado.");

        assertEquals(OrderStatus.DELIVERED, paidOrder.getStatus());
    }

    @Test
    void shouldRejectSellerTryingToGenerateShipments() {
        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> shipmentService.createShipments(
                        seller, paidOrder.getId())
        );

        assertTrue(shipmentRepository.findAll().isEmpty());
    }

    @Test
    void shouldRejectAllocationFromInactiveWarehouse() {
        warehouseA.deactivate();

        assertThrows(
                IllegalStateException.class,
                () -> shipmentService.createShipments(
                        logistics, paidOrder.getId())
        );

        assertTrue(shipmentRepository.findAll().isEmpty());
    }

    @Test
    void shouldAllowDigitalOnlyOrderWithoutShipment() {
        Product digitalProduct = new Product(
                UUID.randomUUID(),
                seller.getId(),
                "Licencia digital",
                "Producto digital",
                ProductType.DIGITAL,
                new BigDecimal("50000")
        );
        digitalProduct.publish();
        productRepository.save(digitalProduct);

        Order digitalOrder = new Order(
                UUID.randomUUID(),
                buyer.getId(),
                List.of(new OrderItem(
                        digitalProduct.getId(),
                        digitalProduct.getName(),
                        new BigDecimal("50000"),
                        1
                ))
        );
        digitalOrder.registerPayment(true);
        orderRepository.save(digitalOrder);

        List<Shipment> result =
                shipmentService.createShipments(logistics, digitalOrder.getId());

        assertTrue(result.isEmpty());
        assertTrue(shipmentRepository.findByOrderId(
                digitalOrder.getId()).isEmpty());
    }

    @Test
    void shouldRollbackShipmentTransitionWhenShipmentSaveFails() {
        FailingShipmentRepository failingRepository =
                new FailingShipmentRepository();

        ShipmentService failingService = createService(failingRepository);

        List<Shipment> shipments =
                failingService.createShipments(logistics, paidOrder.getId());

        Shipment shipment = shipments.get(0);
        failingRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> failingService.packShipment(
                        logistics, shipment.getId(), "Empaque.")
        );

        assertEquals(ShipmentStatus.PREPARING, shipment.getStatus());
        assertTrue(shipment.getTrackingHistory().isEmpty());
    }

    @Test
    void shouldRollbackShipmentWhenOrderSaveFails() {
        List<Shipment> shipments =
                shipmentService.createShipments(logistics, paidOrder.getId());

        for (Shipment shipment : shipments) {
            shipmentService.packShipment(
                    logistics, shipment.getId(), "Empaque.");
        }

        shipmentService.dispatchShipment(
                logistics, shipments.get(0).getId(), "Primer despacho.");

        Shipment second = shipments.get(1);
        orderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> shipmentService.dispatchShipment(
                        logistics, second.getId(), "Segundo despacho.")
        );

        assertEquals(ShipmentStatus.PACKED, second.getStatus());
        assertEquals(1, second.getTrackingHistory().size());
        assertEquals(OrderStatus.PAID, paidOrder.getStatus());
    }

    @Test
    void shouldRollbackFinalDeliveryWhenOrderSaveFails() {
        List<Shipment> shipments =
                shipmentService.createShipments(logistics, paidOrder.getId());

        // Preparar los dos envíos.
        for (Shipment shipment : shipments) {
            shipmentService.packShipment(
                    logistics, shipment.getId(), "Empaque.");
        }

        // Despachar los dos envíos.
        for (Shipment shipment : shipments) {
            shipmentService.dispatchShipment(
                    logistics, shipment.getId(), "Despacho.");
        }

        // Marcar los dos envíos en tránsito.
        for (Shipment shipment : shipments) {
            shipmentService.markShipmentInTransit(
                    logistics, shipment.getId(), "En tránsito.");
        }

        Shipment first = shipments.get(0);
        Shipment last = shipments.get(1);

        assertEquals(OrderStatus.DISPATCHED, paidOrder.getStatus());

        // Entregar el primero correctamente.
        shipmentService.deliverShipment(
                logistics, first.getId(), "Primera entrega.");

        assertEquals(ShipmentStatus.DELIVERED, first.getStatus());

        // Simular un error al guardar el pedido durante la última entrega.
        orderRepository.simulateNextSaveFailure();

        assertThrows(
                IllegalStateException.class,
                () -> shipmentService.deliverShipment(
                        logistics, last.getId(), "Última entrega.")
        );

        // Comprobar que ambos estados se revirtieron correctamente.
        assertEquals(ShipmentStatus.IN_TRANSIT, last.getStatus());
        assertEquals(3, last.getTrackingHistory().size());
        assertEquals(OrderStatus.DISPATCHED, paidOrder.getStatus());

        // Reintentar la entrega debe funcionar.
        shipmentService.deliverShipment(
                logistics, last.getId(), "Reintento de entrega.");

        assertEquals(ShipmentStatus.DELIVERED, last.getStatus());
        assertEquals(OrderStatus.DELIVERED, paidOrder.getStatus());
    }

    @Test
    void shouldAllowBuyerToReadOwnShipmentsButNotAnotherBuyers() {
        List<Shipment> shipments =
                shipmentService.createShipments(logistics, paidOrder.getId());

        assertEquals(
                2,
                shipmentService.getOrderShipments(
                        buyer, paidOrder.getId()).size()
        );

        User otherBuyer = createUser(
                "Otro comprador", "other@shipment.test",
                "BUYER-5102", UserRole.BUYER);
        userRepository.save(otherBuyer);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> shipmentService.getOrderShipments(
                        otherBuyer, paidOrder.getId())
        );

        assertNotNull(
                shipmentService.getShipment(buyer, shipments.get(0).getId())
        );
    }

    private ShipmentService createService(
            InMemoryShipmentRepository repository) {

        return new ShipmentService(
                repository,
                allocationRepository,
                orderRepository,
                productRepository,
                warehouseRepository,
                userRepository
        );
    }

    private Order createPaidPhysicalOrder(int quantity) {
        Order order = new Order(
                UUID.randomUUID(),
                buyer.getId(),
                List.of(new OrderItem(
                        physicalProduct.getId(),
                        physicalProduct.getName(),
                        new BigDecimal("120000"),
                        quantity
                ))
        );

        order.registerPayment(true);
        orderRepository.save(order);

        return order;
    }

    private OrderAllocation allocation(
            Order order, Warehouse warehouse, int quantity) {

        return new OrderAllocation(
                UUID.randomUUID(),
                order.getId(),
                physicalProduct.getId(),
                physicalProduct.getName(),
                warehouse.getId(),
                quantity
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
                new com.nexusmarket.domain.valueobjects.Email(email),
                new com.nexusmarket.domain.valueobjects.DocumentId(document),
                role
        );
    }
}
