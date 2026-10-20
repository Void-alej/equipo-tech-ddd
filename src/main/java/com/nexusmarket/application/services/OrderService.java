package com.nexusmarket.application.services;

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
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.InventoryRepository;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    // Constructor que activa la integración con inventario.
    public OrderService(
            CartService cartService,
            OrderRepository orderRepository,
            UserRepository userRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            User inventoryActor) {

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

        this.cartService = cartService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.inventoryActor = inventoryActor;
    }

    public Order createOrderFromCart(User actor, UUID cartId) {
        requireRole(actor, UserRole.BUYER);

        Cart cart = cartService.getCart(actor, cartId);

        List<OrderItem> items = cart.getItems().stream()
                .map(this::toOrderItem)
                .toList();

        Order order = new Order(UUID.randomUUID(), actor.getId(), items);
        List<Reservation> createdReservations = new ArrayList<>();

        try {
            if (inventoryEnabled()) {
                for (OrderItem item : order.getItems()) {
                    Product product = productRepository.findById(item.getProductId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "No existe el producto del pedido."));

                    if (product.getType() == ProductType.PHYSICAL) {
                        reserveAcrossWarehouses(item, createdReservations);
                    }
                }
            }

            // El carrito se convierte solo después de reservar el inventario.
            cartService.convertCart(actor, cartId);
            orderRepository.save(order);

            if (!createdReservations.isEmpty()) {
                reservations.put(order.getId(),
                        new ArrayList<>(createdReservations));
            }

            return order;
        } catch (RuntimeException exception) {
            releaseReservations(createdReservations,
                    "Liberación por fallo al crear el pedido");
            throw exception;
        }
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

        // Primero se realizan los movimientos de inventario.
        List<Reservation> orderReservations =
                reservations.getOrDefault(orderId, List.of());

        if (approved && inventoryEnabled()) {
            for (Reservation reservation : orderReservations) {
                inventoryService.registerSale(
                        inventoryActor,
                        reservation.productId(),
                        reservation.warehouseId(),
                        reservation.quantity(),
                        "Venta confirmada por pago del pedido"
                );
            }
        }

        order.registerPayment(approved);
        orderRepository.save(order);

        if (!approved && inventoryEnabled()) {
            releaseReservations(orderReservations,
                    "Liberación por pago rechazado");
            reservations.remove(orderId);
        }

        return order;
    }

    public Order dispatchOrder(User actor, UUID orderId) {
        requireOperationalRole(actor);

        Order order = findOrderOrThrow(orderId);
        order.dispatch();
        orderRepository.save(order);

        return order;
    }

    public Order markOrderDelivered(User actor, UUID orderId) {
        requireOperationalRole(actor);

        Order order = findOrderOrThrow(orderId);
        order.markDelivered();
        orderRepository.save(order);

        return order;
    }

    public Order finalizeOrder(User actor, UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        requireOrderBuyer(actor, order);

        order.finalizeOrder();
        orderRepository.save(order);
        reservations.remove(orderId);

        return order;
    }

    public Order cancelOrder(User actor, UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        requireOrderBuyer(actor, order);

        OrderStatus previousStatus = order.getStatus();

        if (inventoryEnabled()) {
            List<Reservation> orderReservations =
                    reservations.getOrDefault(orderId, List.of());

            if (previousStatus == OrderStatus.PENDING_PAYMENT) {
                releaseReservations(orderReservations,
                        "Liberación por cancelación del pedido");
            } else if (previousStatus == OrderStatus.PAID) {
                // El modelo actual permite cancelar pedidos pagados antes
                // del despacho. Se devuelve la cantidad al inventario.
                for (Reservation reservation : orderReservations) {
                    inventoryService.registerReturn(
                            inventoryActor,
                            reservation.productId(),
                            reservation.warehouseId(),
                            reservation.quantity(),
                            "Reintegro de inventario por cancelación del pedido"
                    );
                }
            }
        }

        order.cancel();
        orderRepository.save(order);
        reservations.remove(orderId);

        return order;
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
