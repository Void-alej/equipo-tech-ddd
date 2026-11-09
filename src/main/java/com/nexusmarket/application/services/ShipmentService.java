package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;
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
import com.nexusmarket.domain.models.ShipmentItem;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.OrderAllocationRepository;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.ShipmentRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderAllocationRepository allocationRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            OrderAllocationRepository allocationRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository) {

        if (shipmentRepository == null
                || allocationRepository == null
                || orderRepository == null
                || productRepository == null
                || warehouseRepository == null
                || userRepository == null) {
            throw new IllegalArgumentException(
                    "Todos los repositorios del servicio logístico son obligatorios.");
        }

        this.shipmentRepository = shipmentRepository;
        this.allocationRepository = allocationRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Genera un envío por cada bodega que tenga productos asignados.
     * Los pedidos exclusivamente digitales no necesitan envíos físicos.
     */
    public List<Shipment> createShipments(User actor, UUID orderId) {
        requireOperationalRole(actor);

        Order order = findOrderOrThrow(orderId);

        synchronized (order) {
            if (order.getPaymentStatus() != PaymentStatus.APPROVED
                    || order.getStatus() != OrderStatus.PAID) {
                throw new IllegalStateException(
                        "Solo se pueden generar envíos de pedidos pagados.");
            }

            if (!shipmentRepository.findByOrderId(orderId).isEmpty()) {
                throw new IllegalStateException(
                        "El pedido ya tiene envíos registrados.");
            }

            List<OrderAllocation> allocations =
                    allocationRepository.findByOrderId(orderId);

            Map<UUID, Integer> expectedPhysicalQuantities =
                    new LinkedHashMap<>();

            Map<UUID, String> productNames = new LinkedHashMap<>();

            // Determinar qué productos del pedido requieren envío físico.
            for (OrderItem item : order.getItems()) {
                Product product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No existe un producto del pedido."));

                if (product.getType() == ProductType.PHYSICAL) {
                    int previous = expectedPhysicalQuantities.getOrDefault(
                            item.getProductId(), 0);

                    expectedPhysicalQuantities.put(
                            item.getProductId(),
                            Math.addExact(previous, item.getQuantity())
                    );

                    productNames.put(
                            item.getProductId(),
                            item.getProductName()
                    );
                }
            }

            Map<UUID, Integer> allocatedQuantities = new LinkedHashMap<>();
            Map<UUID, List<ShipmentItem>> itemsByWarehouse =
                    new LinkedHashMap<>();

            // Agrupar las asignaciones por bodega de origen.
            for (OrderAllocation allocation : allocations) {
                UUID productId = allocation.getProductId();

                if (!expectedPhysicalQuantities.containsKey(productId)) {
                    throw new IllegalStateException(
                            "Existe una asignación para un producto que "
                                    + "no requiere envío físico en este pedido.");
                }

                Warehouse warehouse = warehouseRepository
                        .findById(allocation.getWarehouseId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No existe una bodega de origen de la asignación."));

                if (!warehouse.isActive()
                        || warehouse.getType() != WarehouseType.MARKETPLACE) {
                    throw new IllegalStateException(
                            "La bodega de origen debe estar activa "
                                    + "y pertenecer al marketplace.");
                }

                int previous = allocatedQuantities.getOrDefault(productId, 0);

                int newQuantity = Math.addExact(
                        previous, allocation.getQuantity());

                if (newQuantity > expectedPhysicalQuantities.get(productId)) {
                    throw new IllegalStateException(
                            "La cantidad asignada supera la cantidad "
                                    + "comprada del producto.");
                }

                allocatedQuantities.put(productId, newQuantity);

                ShipmentItem shipmentItem = new ShipmentItem(
                        productId,
                        productNames.get(productId),
                        allocation.getQuantity()
                );

                itemsByWarehouse.computeIfAbsent(
                        warehouse.getId(), ignored -> new ArrayList<>()
                ).add(shipmentItem);
            }

            // No generar envíos si falta alguna unidad física por asignar.
            for (Map.Entry<UUID, Integer> expected
                    : expectedPhysicalQuantities.entrySet()) {

                int allocated = allocatedQuantities.getOrDefault(
                        expected.getKey(), 0);

                if (allocated != expected.getValue()) {
                    throw new IllegalStateException(
                            "La asignación de inventario está incompleta "
                                    + "para el producto: "
                                    + productNames.get(expected.getKey()));
                }
            }

            List<Shipment> shipments = new ArrayList<>();

            for (Map.Entry<UUID, List<ShipmentItem>> entry
                    : itemsByWarehouse.entrySet()) {

                shipments.add(new Shipment(
                        UUID.randomUUID(),
                        order.getId(),
                        entry.getKey(),
                        entry.getValue()
                ));
            }

            shipmentRepository.saveAll(shipments);

            return List.copyOf(shipments);
        }
    }

    public Shipment packShipment(
            User actor, UUID shipmentId, String description) {

        return transitionShipment(
                actor,
                shipmentId,
                (shipment, actorId) -> shipment.pack(actorId, description)
        );
    }

    public Shipment dispatchShipment(
            User actor, UUID shipmentId, String description) {

        return transitionShipment(
                actor,
                shipmentId,
                (shipment, actorId) -> shipment.dispatch(actorId, description)
        );
    }

    public Shipment markShipmentInTransit(
            User actor, UUID shipmentId, String description) {

        return transitionShipment(
                actor,
                shipmentId,
                (shipment, actorId) ->
                        shipment.markInTransit(actorId, description)
        );
    }

    public Shipment deliverShipment(
            User actor, UUID shipmentId, String description) {

        return transitionShipment(
                actor,
                shipmentId,
                (shipment, actorId) -> shipment.deliver(actorId, description)
        );
    }

    private Shipment transitionShipment(
            User actor,
            UUID shipmentId,
            BiConsumer<Shipment, UUID> transition) {

        User operator = requireOperationalRole(actor);

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el envío indicado."));

        Order order = findOrderOrThrow(shipment.getOrderId());

        // Mantener el orden de bloqueo: pedido y después envío.
        synchronized (order) {
            if (order.getStatus() != OrderStatus.PAID
                    && order.getStatus() != OrderStatus.DISPATCHED) {
                throw new IllegalStateException(
                        "El pedido no admite actualizaciones logísticas "
                                + "en su estado actual.");
            }

            synchronized (shipment) {
                shipment.transitionAndPersist(
                        () -> transition.accept(shipment, operator.getId()),
                        () -> {
                            shipmentRepository.save(shipment);
                            synchronizeOrderStatus(order);
                        }
                );
            }
        }

        return shipment;
    }

    /**
     * El pedido avanza cuando todos sus envíos han alcanzado
     * el estado logístico correspondiente.
     */
    private void synchronizeOrderStatus(Order order) {
        List<Shipment> shipments =
                shipmentRepository.findByOrderId(order.getId());

        if (shipments.isEmpty()) {
            return;
        }

        boolean allDispatched = shipments.stream().allMatch(
                shipment -> shipment.getStatus() == ShipmentStatus.DISPATCHED
                        || shipment.getStatus() == ShipmentStatus.IN_TRANSIT
                        || shipment.getStatus() == ShipmentStatus.DELIVERED
        );

        if (allDispatched && order.getStatus() == OrderStatus.PAID) {
            order.transitionAndPersist(
                    order::dispatch,
                    () -> orderRepository.save(order)
            );
        }

        boolean allDelivered = shipments.stream().allMatch(
                shipment -> shipment.getStatus() == ShipmentStatus.DELIVERED
        );

        if (allDelivered && order.getStatus() == OrderStatus.DISPATCHED) {
            order.transitionAndPersist(
                    order::markDelivered,
                    () -> orderRepository.save(order)
            );
        }
    }

    /**
     * El comprador consulta únicamente sus propios envíos.
     * Administración, supervisión y logística pueden consultar
     * los envíos del marketplace.
     */
    public List<Shipment> getOrderShipments(User actor, UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        authorizeRead(actor, order);

        return List.copyOf(shipmentRepository.findByOrderId(orderId));
    }

    public Shipment getShipment(User actor, UUID shipmentId) {
        if (shipmentId == null) {
            throw new IllegalArgumentException(
                    "El identificador del envío es obligatorio.");
        }

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el envío indicado."));

        Order order = findOrderOrThrow(shipment.getOrderId());
        authorizeRead(actor, order);

        return shipment;
    }

    private void authorizeRead(User actor, Order order) {
        User registered = findActiveRegisteredUser(actor);

        if (registered.getRole() == UserRole.BUYER) {
            if (!registered.getId().equals(order.getBuyerId())) {
                throw new UnauthorizedUserOperationException(
                        "No puedes consultar envíos de pedidos de otros compradores.");
            }
            return;
        }

        if (registered.getRole() != UserRole.ADMINISTRATOR
                && registered.getRole() != UserRole.SUPERVISOR
                && registered.getRole() != UserRole.LOGISTICS_OPERATOR) {
            throw new UnauthorizedUserOperationException(
                    "No tienes permisos para consultar este envío.");
        }
    }

    private User requireOperationalRole(User actor) {
        User registered = findActiveRegisteredUser(actor);

        if (registered.getRole() != UserRole.LOGISTICS_OPERATOR
                && registered.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo logística o administración puede operar los envíos.");
        }

        return registered;
    }

    private User findActiveRegisteredUser(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "El usuario debe estar activo.");
        }

        User registered = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedUserOperationException(
                        "El usuario no está registrado."));

        if (!registered.isActive()
                || registered.getRole() != actor.getRole()) {
            throw new UnauthorizedUserOperationException(
                    "El usuario no está activo o sus permisos no coinciden.");
        }

        return registered;
    }

    private Order findOrderOrThrow(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException(
                    "El identificador del pedido es obligatorio.");
        }

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el pedido indicado."));
    }
}
