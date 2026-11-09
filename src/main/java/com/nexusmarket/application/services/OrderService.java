
package com.nexusmarket.application.services;

import com.nexusmarket.application.dto.SellerOrderView;
import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.PaymentStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Cart;
import com.nexusmarket.domain.models.CartItem;
import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderAllocation;
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.InventoryRepository;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.OrderAllocationRepository;
import com.nexusmarket.domain.ports.out.ShipmentRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public class OrderService {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    // Dependencias opcionales para habilitar la integración con inventario.
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final User inventoryActor;
    private final OrderAllocationRepository orderAllocationRepository;
    private final ShipmentRepository shipmentRepository;

    private final Map<UUID, List<Reservation>> reservations =
            new ConcurrentHashMap<>();

    private record Reservation(UUID productId, UUID warehouseId, int quantity) {}

    // Constructor compatible con las pruebas y usos anteriores.
    public OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository) {

        this(cartService, orderRepository, userRepository,
                null, null, null, null, null);
    }

    // Constructor para operaciones que necesitan consultar productos,
    // pero no activar la integración con inventario.
    public OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ProductRepository productRepository) {

        if (cartService == null || orderRepository == null
                || userRepository == null || productRepository == null) {
            throw new IllegalArgumentException(
                    "Las dependencias del servicio son obligatorias.");
        }

        this.cartService = cartService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;

        this.inventoryService = null;
        this.inventoryRepository = null;
        this.warehouseRepository = null;
        this.inventoryActor = null;
        this.orderAllocationRepository = null;
        this.shipmentRepository = null;
    }

    // Constructor compatible con la integración anterior de inventario.
    public OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            User inventoryActor) {

        this(
                cartService,
                orderRepository,
                userRepository,
                inventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                inventoryActor,
                null,
                null,
                false,
                false
        );
    }

    // Constructor con persistencia de asignaciones para logística.
    public OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            User inventoryActor,
            OrderAllocationRepository orderAllocationRepository) {

        this(
                cartService,
                orderRepository,
                userRepository,
                inventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                inventoryActor,
                orderAllocationRepository,
                null,
                true,
                false
        );
    }

    // Constructor integrado con asignaciones y seguimiento de envíos.
    public OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            User inventoryActor,
            OrderAllocationRepository orderAllocationRepository,
            ShipmentRepository shipmentRepository) {

        this(
                cartService,
                orderRepository,
                userRepository,
                inventoryService,
                inventoryRepository,
                warehouseRepository,
                productRepository,
                inventoryActor,
                orderAllocationRepository,
                shipmentRepository,
                true,
                true
        );
    }

    private OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            User inventoryActor,
            OrderAllocationRepository orderAllocationRepository,
            ShipmentRepository shipmentRepository,
            boolean requireAllocationRepository,
            boolean requireShipmentRepository) {

        if (cartService == null || orderRepository == null
                || userRepository == null) {
            throw new IllegalArgumentException(
                    "Los servicios y repositorios de pedidos son obligatorios.");
        }

        boolean allInventoryDependenciesPresent =
                inventoryService != null
                && inventoryRepository != null
                && warehouseRepository != null
                && productRepository != null
                && inventoryActor != null;

        boolean allInventoryDependenciesAbsent =
                inventoryService == null
                && inventoryRepository == null
                && warehouseRepository == null
                && productRepository == null
                && inventoryActor == null;

        if (!allInventoryDependenciesPresent && !allInventoryDependenciesAbsent) {
            throw new IllegalArgumentException(
                    "Debes proporcionar todas las dependencias de inventario.");
        }

        if (allInventoryDependenciesPresent
                && (inventoryActor.getRole() != UserRole.LOGISTICS_OPERATOR
                    || !inventoryActor.isActive())) {
            throw new IllegalArgumentException(
                    "El usuario de inventario debe ser un operador logístico activo.");
        }

        if (requireAllocationRepository && orderAllocationRepository == null) {
            throw new IllegalArgumentException(
                    "El repositorio de asignaciones es obligatorio.");
        }

        if (requireShipmentRepository && shipmentRepository == null) {
            throw new IllegalArgumentException(
                    "El repositorio de envíos es obligatorio.");
        }

        this.cartService = cartService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.inventoryActor = inventoryActor;
        this.orderAllocationRepository = orderAllocationRepository;
        this.shipmentRepository = shipmentRepository;
    }

    public Order createOrderFromCart(User actor, UUID cartId) {
        requireRole(actor, UserRole.BUYER);

        Cart cart = cartService.getCart(actor, cartId);

        synchronized (cart) {
            return createOrderFromCartLocked(actor, cartId, cart);
        }
    }

    private Order createOrderFromCartLocked(
            User actor, UUID cartId, Cart cart) {

        // La validación funciona incluso sin integración de inventario.
        cartService.validateProductsAvailable(actor, cartId);

        List<OrderItem> items = cart.getItems().stream()
                .map(this::toOrderItem)
                .toList();

        Order order = new Order(UUID.randomUUID(), actor.getId(), items);
        List<Reservation> createdReservations = new ArrayList<>();
        boolean cartConverted = false;
        boolean allocationSaveAttempted = false;

        try {
            if (inventoryEnabled()) {
                for (OrderItem item : order.getItems()) {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No existe el producto del pedido."));

                    // Segunda comprobación antes de reservar inventario.
                    if (!product.isAvailableForSale()) {
                        throw new IllegalStateException(
                                "El producto ya no está publicado ni disponible para la venta: "
                                        + product.getName());
                    }

                    if (product.getType() == ProductType.PHYSICAL) {
                        reserveAcrossWarehouses(item, createdReservations);
                    }
                }
            }

            // El carrito se convierte solo después de reservar el inventario.
            cartService.convertCart(actor, cartId);
            cartConverted = true;

            if (orderAllocationRepository != null
                    && !createdReservations.isEmpty()) {
                allocationSaveAttempted = true;
                orderAllocationRepository.saveAll(
                        toOrderAllocations(order, createdReservations));
            }

            orderRepository.save(order);

            if (!createdReservations.isEmpty()) {
                reservations.put(order.getId(),
                        new ArrayList<>(createdReservations));
            }

            return order;
        } catch (RuntimeException exception) {
            if (allocationSaveAttempted) {
                try {
                    orderAllocationRepository.deleteByOrderId(order.getId());
                } catch (RuntimeException compensationException) {
                    exception.addSuppressed(compensationException);
                }
            }

            try {
                releaseReservations(createdReservations,
                        "Liberación por fallo al crear el pedido");
            } catch (RuntimeException compensationException) {
                exception.addSuppressed(compensationException);
            }

            if (cartConverted) {
                try {
                    cartService.restoreConvertedCart(actor, cartId);
                } catch (RuntimeException compensationException) {
                    exception.addSuppressed(compensationException);
                }
            }

            throw exception;
        }
    }

    private List<OrderAllocation> toOrderAllocations(
            Order order, List<Reservation> createdReservations) {

        List<OrderAllocation> allocations = new ArrayList<>();

        for (Reservation reservation : createdReservations) {
            OrderItem orderItem = order.getItems().stream()
                    .filter(item -> item.getProductId()
                            .equals(reservation.productId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No se encontró el producto de una reserva."));

            allocations.add(new OrderAllocation(
                    UUID.randomUUID(),
                    order.getId(),
                    reservation.productId(),
                    orderItem.getProductName(),
                    reservation.warehouseId(),
                    reservation.quantity()
            ));
        }

        return List.copyOf(allocations);
    }

    private void reserveAcrossWarehouses(
            OrderItem item, List<Reservation> createdReservations) {

        int remaining = item.getQuantity();

        List<Inventory> inventories =
                inventoryRepository.findByProductId(item.getProductId());

        for (Inventory inventory : inventories) {
            if (remaining == 0) {
                break;
            }

            Warehouse warehouse = warehouseRepository
                    .findById(inventory.getWarehouseId())
                    .orElse(null);

            if (warehouse == null || !warehouse.isActive()
                    || warehouse.getType() != WarehouseType.MARKETPLACE) {
                continue;
            }

            int available = inventory.getAvailableQuantity();
            if (available <= 0) {
                continue;
            }

            int quantityToReserve = Math.min(remaining, available);

            inventoryService.reserveStock(
                    inventoryActor,
                    item.getProductId(),
                    warehouse.getId(),
                    quantityToReserve,
                    "Reserva para pedido"
            );

            createdReservations.add(new Reservation(
                    item.getProductId(),
                    warehouse.getId(),
                    quantityToReserve
            ));

            remaining -= quantityToReserve;
        }

        if (remaining > 0) {
            throw new IllegalStateException(
                    "Inventario insuficiente para el producto: "
                            + item.getProductName());
        }
    }

    public Order registerPayment(User actor, UUID orderId, boolean approved) {
        Order order = findOrderOrThrow(orderId);
        requireOrderBuyer(actor, order);

        synchronized (order) {
            return registerPaymentLocked(actor, orderId, approved, order);
        }
    }

    private Order registerPaymentLocked(
            User actor, UUID orderId, boolean approved, Order order) {

        // Validar el estado antes de modificar el inventario.
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "El pedido no está pendiente de pago.");
        }

        List<Reservation> orderReservations =
                new ArrayList<>(reservations.getOrDefault(orderId, List.of()));

        List<Reservation> completedSales = new ArrayList<>();
        List<Reservation> releasedReservations = new ArrayList<>();

        // Modificar inventario antes de registrar el pago.
        if (inventoryEnabled()) {
            try {
                for (Reservation reservation : orderReservations) {
                    if (approved) {
                        inventoryService.registerSale(
                                inventoryActor,
                                reservation.productId(),
                                reservation.warehouseId(),
                                reservation.quantity(),
                                "Venta confirmada por pago del pedido"
                        );

                        completedSales.add(reservation);
                    } else {
                        inventoryService.releaseReservation(
                                inventoryActor,
                                reservation.productId(),
                                reservation.warehouseId(),
                                reservation.quantity(),
                                "Liberación por pago rechazado"
                        );

                        releasedReservations.add(reservation);
                    }
                }
            } catch (RuntimeException inventoryError) {
                if (approved) {
                    restoreSalesToReservations(
                            completedSales, inventoryError);
                } else {
                    restoreReleasedReservations(
                            releasedReservations, inventoryError);
                }

                throw inventoryError;
            }
        }

        // Registrar y guardar el cambio de estado.
        try {
            order.transitionAndPersist(
                    () -> order.registerPayment(approved),
                    () -> orderRepository.save(order));

        } catch (RuntimeException orderError) {
            if (inventoryEnabled()) {
                if (approved) {
                    restoreSalesToReservations(
                            completedSales, orderError);
                } else {
                    restoreReleasedReservations(
                            releasedReservations, orderError);
                }
            }

            throw orderError;
        }

        // Las reservas se eliminan únicamente después de guardar
        // correctamente el rechazo del pago.
        if (!approved && inventoryEnabled()) {
            reservations.remove(orderId);
        }

        return order;
    }

    /**
     * Transición heredada, disponible únicamente cuando no está
     * habilitada la integración logística con inventario.
     * En el flujo integrado debe utilizarse ShipmentService.
     */
    @Deprecated
    public Order dispatchOrder(User actor, UUID orderId) {
        requireOperationalRole(actor);
        requireLegacyOrderTransitions();

        Order order = findOrderOrThrow(orderId);
        order.transitionAndPersist(
                order::dispatch,
                () -> orderRepository.save(order));

        return order;
    }

    /**
     * Transición heredada, disponible únicamente cuando no está
     * habilitada la integración logística con inventario.
     * En el flujo integrado debe utilizarse ShipmentService.
     */
    @Deprecated
    public Order markOrderDelivered(User actor, UUID orderId) {
        requireOperationalRole(actor);
        requireLegacyOrderTransitions();

        Order order = findOrderOrThrow(orderId);
        order.transitionAndPersist(
                order::markDelivered,
                () -> orderRepository.save(order));

        return order;
    }

    public Order finalizeOrder(User actor, UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        requireOrderBuyer(actor, order);

        synchronized (order) {
            order.transitionAndPersist(
                    order::finalizeOrder,
                    () -> orderRepository.save(order));
            reservations.remove(orderId);

            return order;
        }
    }

    public Order cancelOrder(User actor, UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        requireOrderBuyer(actor, order);

        synchronized (order) {
            return cancelOrderLocked(actor, orderId, order);
        }
    }

    private Order cancelOrderLocked(
            User actor, UUID orderId, Order order) {

        OrderStatus previousStatus = order.getStatus();

        if (previousStatus != OrderStatus.PENDING_PAYMENT
                && previousStatus != OrderStatus.PAID) {
            throw new IllegalStateException(
                    "El pedido no se encuentra en un estado que permita cancelarlo.");
        }

        if (previousStatus == OrderStatus.PAID) {
            if (shipmentRepository == null) {
                if (inventoryEnabled() || orderAllocationRepository != null) {
                    throw new IllegalStateException(
                            "No se puede cancelar un pedido pagado sin "
                                    + "consultar su repositorio de envíos.");
                }
            } else if (!shipmentRepository.findByOrderId(orderId).isEmpty()) {
                throw new IllegalStateException(
                        "No se puede cancelar un pedido que ya tiene envíos "
                                + "registrados. Debe resolverse primero "
                                + "su proceso logístico.");
            }
        }

        List<Reservation> orderReservations =
                new ArrayList<>(reservations.getOrDefault(orderId, List.of()));

        List<Reservation> releasedReservations = new ArrayList<>();
        List<Reservation> completedReturns = new ArrayList<>();

        // Liberar reservas o registrar devoluciones según el estado.
        if (inventoryEnabled()) {
            try {
                if (previousStatus == OrderStatus.PENDING_PAYMENT) {
                    for (Reservation reservation : orderReservations) {
                        inventoryService.releaseReservation(
                                inventoryActor,
                                reservation.productId(),
                                reservation.warehouseId(),
                                reservation.quantity(),
                                "Liberación por cancelación del pedido"
                        );

                        releasedReservations.add(reservation);
                    }

                } else if (previousStatus == OrderStatus.PAID) {
                    for (Reservation reservation : orderReservations) {
                        inventoryService.registerReturn(
                                inventoryActor,
                                reservation.productId(),
                                reservation.warehouseId(),
                                reservation.quantity(),
                                "Reintegro de inventario por cancelación del pedido"
                        );

                        completedReturns.add(reservation);
                    }
                }

            } catch (RuntimeException inventoryError) {
                if (previousStatus == OrderStatus.PENDING_PAYMENT) {
                    restoreReleasedReservations(
                            releasedReservations, inventoryError);
                } else {
                    reverseCompletedReturns(
                            completedReturns, inventoryError);
                }

                throw inventoryError;
            }
        }

        // Cambiar y guardar el pedido.
        try {
            order.transitionAndPersist(
                    order::cancel,
                    () -> orderRepository.save(order));

        } catch (RuntimeException orderError) {
            if (inventoryEnabled()) {
                if (previousStatus == OrderStatus.PENDING_PAYMENT) {
                    restoreReleasedReservations(
                            releasedReservations, orderError);
                } else {
                    reverseCompletedReturns(
                            completedReturns, orderError);
                }
            }

            throw orderError;
        }

        reservations.remove(orderId);

        return order;
    }

    /**
     * Consulta los pedidos que contienen productos del vendedor.
     * Cada resultado muestra solamente sus propios artículos.
     */
    public List<SellerOrderView> getMySellerOrders(User actor) {
        requireRole(actor, UserRole.SELLER);

        Set<UUID> sellerProductIds = findSellerProductIds(actor);

        List<SellerOrderView> result = new ArrayList<>();

        for (Order order : orderRepository.findAll()) {
            List<OrderItem> sellerItems = order.getItems().stream()
                    .filter(item -> sellerProductIds.contains(item.getProductId()))
                    .toList();

            if (!sellerItems.isEmpty()) {
                result.add(toSellerOrderView(order, sellerItems));
            }
        }

        return List.copyOf(result);
    }

    /**
     * Consulta un pedido concreto si contiene productos del vendedor.
     * No expone los artículos de otros vendedores.
     */
    public SellerOrderView getSellerOrder(User actor, UUID orderId) {
        requireRole(actor, UserRole.SELLER);

        Order order = findOrderOrThrow(orderId);
        Set<UUID> sellerProductIds = findSellerProductIds(actor);

        List<OrderItem> sellerItems = order.getItems().stream()
                .filter(item -> sellerProductIds.contains(item.getProductId()))
                .toList();

        if (sellerItems.isEmpty()) {
            throw new UnauthorizedUserOperationException(
                    "Este pedido no contiene productos de tu propiedad.");
        }

        return toSellerOrderView(order, sellerItems);
    }

    private Set<UUID> findSellerProductIds(User actor) {
        if (productRepository == null) {
            throw new IllegalStateException(
                    "La consulta de pedidos de vendedor requiere ProductRepository.");
        }

        return productRepository.findBySellerId(actor.getId()).stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
    }

    private SellerOrderView toSellerOrderView(
            Order order, List<OrderItem> sellerItems) {

        return new SellerOrderView(
                order.getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getCreatedAt(),
                sellerItems
        );
    }

    public Order getOrder(User actor, UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        requireActiveRegisteredUser(actor);

        if (actor.getRole() == UserRole.BUYER) {
            requireOrderBuyer(actor, order);
        } else if (actor.getRole() != UserRole.ADMINISTRATOR
                && actor.getRole() != UserRole.SUPERVISOR
                && actor.getRole() != UserRole.LOGISTICS_OPERATOR) {
            throw new UnauthorizedUserOperationException(
                    "No tienes permiso para consultar este pedido.");
        }

        return order;
    }

    public List<Order> getMyOrders(User actor) {
        requireRole(actor, UserRole.BUYER);
        return orderRepository.findByBuyerId(actor.getId());
    }

    private void releaseReservations(
            List<Reservation> items, String description) {

        if (!inventoryEnabled()) {
            return;
        }

        // Intentar liberar todas las reservas, aunque una operación falle.
        RuntimeException firstFailure = null;

        for (Reservation reservation : items) {
            try {
                inventoryService.releaseReservation(
                        inventoryActor,
                        reservation.productId(),
                        reservation.warehouseId(),
                        reservation.quantity(),
                        description
                );
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }

        if (firstFailure != null) {
            throw new IllegalStateException(
                    "No se pudieron liberar todas las reservas.", firstFailure);
        }
    }

    private void restoreSalesToReservations(
            List<Reservation> completedSales,
            RuntimeException originalError) {

        for (Reservation reservation : completedSales) {
            try {
                inventoryService.registerReturn(
                        inventoryActor,
                        reservation.productId(),
                        reservation.warehouseId(),
                        reservation.quantity(),
                        "Reversión técnica de venta por fallo en el pedido"
                );

                inventoryService.reserveStock(
                        inventoryActor,
                        reservation.productId(),
                        reservation.warehouseId(),
                        reservation.quantity(),
                        "Restauración de reserva por fallo en el pedido"
                );

            } catch (RuntimeException compensationError) {
                originalError.addSuppressed(compensationError);
            }
        }
    }

    private void restoreReleasedReservations(
            List<Reservation> releasedReservations,
            RuntimeException originalError) {

        for (Reservation reservation : releasedReservations) {
            try {
                inventoryService.reserveStock(
                        inventoryActor,
                        reservation.productId(),
                        reservation.warehouseId(),
                        reservation.quantity(),
                        "Restauración de reserva por fallo en la operación del pedido"
                );

            } catch (RuntimeException compensationError) {
                originalError.addSuppressed(compensationError);
            }
        }
    }

    private void reverseCompletedReturns(
            List<Reservation> completedReturns,
            RuntimeException originalError) {

        for (Reservation reservation : completedReturns) {
            try {
                inventoryService.reverseReturn(
                        inventoryActor,
                        reservation.productId(),
                        reservation.warehouseId(),
                        reservation.quantity(),
                        "Reversión de devolución por fallo en la operación del pedido"
                );

            } catch (RuntimeException compensationError) {
                originalError.addSuppressed(compensationError);
            }
        }
    }

    private boolean inventoryEnabled() {
        return inventoryService != null;
    }

    private OrderItem toOrderItem(CartItem item) {
        return new OrderItem(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity()
        );
    }

    private Order findOrderOrThrow(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException(
                    "El identificador del pedido es obligatorio.");
        }

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el pedido solicitado."));
    }

    private void requireOrderBuyer(User actor, Order order) {
        requireRole(actor, UserRole.BUYER);

        if (!order.getBuyerId().equals(actor.getId())) {
            throw new UnauthorizedUserOperationException(
                    "No puedes modificar el pedido de otro comprador.");
        }
    }

    private void requireLegacyOrderTransitions() {
        if (inventoryEnabled() || orderAllocationRepository != null) {
            throw new IllegalStateException(
                    "El despacho y la entrega deben gestionarse "
                            + "mediante ShipmentService.");
        }
    }

    private void requireOperationalRole(User actor) {
        requireActiveRegisteredUser(actor);

        if (actor.getRole() != UserRole.LOGISTICS_OPERATOR
                && actor.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo logística o un administrador puede realizar esta operación.");
        }
    }

    private void requireRole(User actor, UserRole requiredRole) {
        requireActiveRegisteredUser(actor);

        if (actor.getRole() != requiredRole) {
            throw new UnauthorizedUserOperationException(
                    "El usuario no tiene el rol necesario para esta operación.");
        }
    }

    private void requireActiveRegisteredUser(User actor) {
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
                    "El usuario registrado no está activo o sus permisos no coinciden.");
        }
    }
}
