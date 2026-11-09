package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.OrderStatus;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.ReturnCondition;
import com.nexusmarket.domain.enums.ReturnStatus;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.ReturnRequest;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.ReturnRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ReturnService {

    private final ReturnRepository returnRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;

    public ReturnService(
            ReturnRepository returnRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            InventoryService inventoryService) {

        if (returnRepository == null || orderRepository == null
                || userRepository == null || productRepository == null
                || warehouseRepository == null || inventoryService == null) {
            throw new IllegalArgumentException(
                    "Todas las dependencias de devoluciones son obligatorias.");
        }

        this.returnRepository = returnRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * El comprador puede solicitar devoluciones de pedidos entregados
     * o finalizados. Las solicitudes pendientes, aprobadas y reembolsadas
     * consumen el límite de unidades que pueden devolverse.
     */
    public ReturnRequest requestReturn(
            User actor,
            UUID orderId,
            UUID productId,
            int quantity,
            String reason) {

        requireRole(actor, UserRole.BUYER);

        if (productId == null) {
            throw new IllegalArgumentException(
                    "El identificador del producto es obligatorio.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero.");
        }

        Order order = findOrderOrThrow(orderId);

        if (!order.getBuyerId().equals(actor.getId())) {
            throw new UnauthorizedUserOperationException(
                    "No puedes solicitar devoluciones de otro comprador.");
        }

        synchronized (order) {
            if (order.getStatus() != OrderStatus.DELIVERED
                    && order.getStatus() != OrderStatus.FINALIZED) {
                throw new IllegalStateException(
                        "Solo se admiten devoluciones de pedidos entregados "
                                + "o finalizados.");
            }

            OrderItem purchasedItem = order.getItems().stream()
                    .filter(item -> item.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El producto no pertenece al pedido."));

            int previouslyRequested = 0;

            for (ReturnRequest existing :
                    returnRepository.findByOrderId(orderId)) {

                boolean consumesQuantity =
                        existing.getStatus() != ReturnStatus.REJECTED;

                if (consumesQuantity
                        && existing.getProductId().equals(productId)) {
                    previouslyRequested = Math.addExact(
                            previouslyRequested,
                            existing.getQuantity());
                }
            }

            int remainingQuantity =
                    purchasedItem.getQuantity() - previouslyRequested;

            if (quantity > remainingQuantity) {
                throw new IllegalArgumentException(
                        "La cantidad solicitada supera las unidades "
                                + "disponibles para devolución.");
            }

            BigDecimal refundAmount = purchasedItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(quantity));

            ReturnRequest request = new ReturnRequest(
                    UUID.randomUUID(),
                    order.getId(),
                    actor.getId(),
                    productId,
                    purchasedItem.getProductName(),
                    quantity,
                    refundAmount,
                    reason
            );

            return returnRepository.save(request);
        }
    }

    public ReturnRequest approveReturn(User actor, UUID requestId) {
        requireRole(actor, UserRole.ADMINISTRATOR);

        ReturnRequest request = findRequestOrThrow(requestId);

        synchronized (request) {
            request.transitionAndPersist(
                    request::approve,
                    () -> returnRepository.save(request));
        }

        return request;
    }

    public ReturnRequest rejectReturn(User actor, UUID requestId) {
        requireRole(actor, UserRole.ADMINISTRATOR);

        ReturnRequest request = findRequestOrThrow(requestId);

        synchronized (request) {
            request.transitionAndPersist(
                    request::reject,
                    () -> returnRepository.save(request));
        }

        return request;
    }

    /**
     * Registra la recepción física.
     * Los productos aptos aumentan el inventario.
     * Los dañados quedan registrados sin aumentar el stock disponible.
     */
    public ReturnRequest registerPhysicalReceipt(
            User actor,
            UUID requestId,
            UUID warehouseId,
            ReturnCondition condition) {

        requireOperationalRole(actor);

        if (warehouseId == null || condition == null) {
            throw new IllegalArgumentException(
                    "La bodega y la condición del producto son obligatorias.");
        }

        ReturnRequest request = findRequestOrThrow(requestId);

        synchronized (request) {
            request.validateReceipt(warehouseId, condition);

            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el producto devuelto."));

            if (product.getType() != ProductType.PHYSICAL) {
                throw new IllegalArgumentException(
                        "Los productos digitales no requieren recepción física.");
            }

            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe la bodega indicada."));

            if (!warehouse.isActive()) {
                throw new IllegalStateException(
                        "No se puede recibir una devolución en una bodega inactiva.");
            }

            boolean addedToInventory =
                    condition == ReturnCondition.SELLABLE;

            if (addedToInventory) {
                inventoryService.registerReturn(
                        actor,
                        request.getProductId(),
                        warehouseId,
                        request.getQuantity(),
                        "Reingreso de inventario por devolución " + request.getId()
                );
            }

            try {
                request.transitionAndPersist(
                        () -> request.receive(warehouseId, condition),
                        () -> returnRepository.save(request));

            } catch (RuntimeException persistenceError) {
                if (addedToInventory) {
                    try {
                        inventoryService.reverseReturn(
                                actor,
                                request.getProductId(),
                                warehouseId,
                                request.getQuantity(),
                                "Reversión técnica de devolución " + request.getId()
                        );
                    } catch (RuntimeException rollbackError) {
                        persistenceError.addSuppressed(rollbackError);
                    }
                }

                throw persistenceError;
            }
        }

        return request;
    }

    /**
     * Los productos digitales pueden pasar a reembolso pendiente
     * después de ser aprobados, sin recepción física.
     */
    public ReturnRequest approveDigitalReturnForRefund(
            User actor, UUID requestId) {

        requireRole(actor, UserRole.ADMINISTRATOR);

        ReturnRequest request = findRequestOrThrow(requestId);

        synchronized (request) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el producto solicitado."));

            if (product.getType() != ProductType.DIGITAL) {
                throw new IllegalArgumentException(
                        "Esta operación solo admite productos digitales.");
            }

            request.transitionAndPersist(
                    request::markRefundPendingWithoutPhysicalReceipt,
                    () -> returnRepository.save(request));
        }

        return request;
    }

    /**
     * Registra la confirmación de un reembolso externo.
     * No ejecuta por sí mismo una transferencia de dinero.
     */
    public ReturnRequest markRefunded(
            User actor,
            UUID requestId,
            String refundReference) {

        requireRole(actor, UserRole.ADMINISTRATOR);

        ReturnRequest request = findRequestOrThrow(requestId);

        synchronized (request) {
            request.transitionAndPersist(
                    () -> request.markRefunded(refundReference),
                    () -> returnRepository.save(request));
        }

        return request;
    }

    public List<ReturnRequest> getMyReturns(User actor) {
        requireRole(actor, UserRole.BUYER);
        return returnRepository.findByBuyerId(actor.getId());
    }

    public ReturnRequest getReturn(User actor, UUID requestId) {
        requireActiveRegisteredUser(actor);

        ReturnRequest request = findRequestOrThrow(requestId);

        if (actor.getRole() == UserRole.BUYER) {
            if (!request.getBuyerId().equals(actor.getId())) {
                throw new UnauthorizedUserOperationException(
                        "No puedes consultar devoluciones de otro comprador.");
            }
            return request;
        }

        if (actor.getRole() == UserRole.ADMINISTRATOR
                || actor.getRole() == UserRole.SUPERVISOR) {
            return request;
        }

        throw new UnauthorizedUserOperationException(
                "No tienes permiso para consultar esta devolución.");
    }

    public List<ReturnRequest> getAllReturns(User actor) {
        requireRole(actor, UserRole.ADMINISTRATOR);
        return returnRepository.findAll();
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

    private ReturnRequest findRequestOrThrow(UUID requestId) {
        if (requestId == null) {
            throw new IllegalArgumentException(
                    "El identificador de devolución es obligatorio.");
        }

        return returnRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la solicitud de devolución."));
    }

    private void requireRole(User actor, UserRole requiredRole) {
        requireActiveRegisteredUser(actor);

        if (actor.getRole() != requiredRole) {
            throw new UnauthorizedUserOperationException(
                    "El usuario no tiene el rol necesario para esta operación.");
        }
    }

    private void requireOperationalRole(User actor) {
        requireActiveRegisteredUser(actor);

        if (actor.getRole() != UserRole.ADMINISTRATOR
                && actor.getRole() != UserRole.LOGISTICS_OPERATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo administración o logística puede recibir devoluciones.");
        }
    }

    private void requireActiveRegisteredUser(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "Se requiere un usuario activo.");
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
