package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.PaymentStatus;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Invoice;
import com.nexusmarket.domain.models.InvoiceItem;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.OrderItem;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.InvoiceRepository;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            OrderRepository orderRepository,
            UserRepository userRepository) {

        if (invoiceRepository == null
                || orderRepository == null
                || userRepository == null) {
            throw new IllegalArgumentException(
                    "Los repositorios de facturas, pedidos y usuarios son obligatorios.");
        }

        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    public Invoice issueInvoice(User actor, UUID orderId) {
        User buyer = requireBuyer(actor);

        if (orderId == null) {
            throw new IllegalArgumentException("El identificador del pedido es obligatorio.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el pedido indicado."));

        if (!order.getBuyerId().equals(buyer.getId())) {
            throw new UnauthorizedUserOperationException(
                    "No puedes emitir facturas de pedidos de otros compradores.");
        }

        synchronized (order) {
            if (order.getPaymentStatus() != PaymentStatus.APPROVED) {
                throw new IllegalStateException(
                        "Solo se pueden emitir facturas de pedidos con pago aprobado.");
            }

            if (invoiceRepository.findByOrderId(orderId).isPresent()) {
                throw new IllegalStateException(
                        "El pedido ya tiene una factura emitida.");
            }

            List<InvoiceItem> invoiceItems = order.getItems().stream()
                    .map(this::toInvoiceItem)
                    .toList();

            Invoice invoice = new Invoice(
                    UUID.randomUUID(),
                    order.getId(),
                    buyer.getId(),
                    buyer.getFullName(),
                    buyer.getEmail().getValue(),
                    buyer.getDocumentId().getValue(),
                    invoiceItems,
                    LocalDateTime.now()
            );

            invoiceRepository.save(invoice);
            return invoice;
        }
    }

    public Invoice getMyInvoice(User actor, UUID invoiceId) {
        User buyer = requireBuyer(actor);

        if (invoiceId == null) {
            throw new IllegalArgumentException(
                    "El identificador de la factura es obligatorio.");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la factura indicada."));

        if (!invoice.getBuyerId().equals(buyer.getId())) {
            throw new UnauthorizedUserOperationException(
                    "No puedes consultar facturas de otros compradores.");
        }

        return invoice;
    }

    public List<Invoice> getMyInvoices(User actor) {
        User buyer = requireBuyer(actor);
        return List.copyOf(invoiceRepository.findByBuyerId(buyer.getId()));
    }

    private InvoiceItem toInvoiceItem(OrderItem item) {
        return new InvoiceItem(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity()
        );
    }

    private User requireBuyer(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "Se requiere un comprador activo y registrado.");
        }

        User registeredUser = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedUserOperationException(
                        "El usuario no está registrado."));

        if (!registeredUser.isActive()
                || registeredUser.getRole() != actor.getRole()
                || registeredUser.getRole() != UserRole.BUYER) {
            throw new UnauthorizedUserOperationException(
                    "Esta operación está reservada para compradores activos.");
        }

        return registeredUser;
    }
}
