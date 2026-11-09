package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.ReturnCondition;
import com.nexusmarket.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReturnRequest {

    private final UUID id;
    private final UUID orderId;
    private final UUID buyerId;
    private final UUID productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal refundAmount;
    private final String reason;
    private final LocalDateTime requestedAt;

    private ReturnStatus status;
    private ReturnCondition condition;
    private UUID warehouseId;
    private LocalDateTime receivedAt;
    private String refundReference;
    private LocalDateTime refundedAt;

    public ReturnRequest(
            UUID id,
            UUID orderId,
            UUID buyerId,
            UUID productId,
            String productName,
            int quantity,
            BigDecimal refundAmount,
            String reason) {

        if (id == null || orderId == null || buyerId == null
                || productId == null) {
            throw new IllegalArgumentException(
                    "Los identificadores de la devolución son obligatorios.");
        }

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del producto es obligatorio.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad devuelta debe ser mayor que cero.");
        }

        if (refundAmount == null
                || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "El valor del reembolso debe ser mayor que cero.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "El motivo de la devolución es obligatorio.");
        }

        this.id = id;
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.productId = productId;
        this.productName = productName.trim();
        this.quantity = quantity;
        this.refundAmount = refundAmount;
        this.reason = reason.trim();
        this.requestedAt = LocalDateTime.now();
        this.status = ReturnStatus.REQUESTED;
    }

    public void approve() {
        requireStatus(ReturnStatus.REQUESTED);
        status = ReturnStatus.APPROVED;
    }

    public void reject() {
        requireStatus(ReturnStatus.REQUESTED);
        status = ReturnStatus.REJECTED;
    }

    public void validateReceipt(UUID warehouseId, ReturnCondition condition) {
        requireStatus(ReturnStatus.APPROVED);

        if (warehouseId == null || condition == null) {
            throw new IllegalArgumentException(
                    "La bodega y la condición del producto son obligatorias.");
        }
    }

    public void receive(UUID warehouseId, ReturnCondition condition) {
        validateReceipt(warehouseId, condition);

        this.warehouseId = warehouseId;
        this.condition = condition;
        this.receivedAt = LocalDateTime.now();
        this.status = ReturnStatus.REFUND_PENDING;
    }

    public void markRefundPendingWithoutPhysicalReceipt() {
        requireStatus(ReturnStatus.APPROVED);
        status = ReturnStatus.REFUND_PENDING;
    }

    public void markRefunded(String refundReference) {
        requireStatus(ReturnStatus.REFUND_PENDING);

        if (refundReference == null || refundReference.isBlank()) {
            throw new IllegalArgumentException(
                    "La referencia del reembolso es obligatoria.");
        }

        this.refundReference = refundReference.trim();
        this.refundedAt = LocalDateTime.now();
        this.status = ReturnStatus.REFUNDED;
    }

    /**
     * Ejecuta una transición y la persistencia.
     * Si el guardado falla, restaura el estado anterior.
     */
    public void transitionAndPersist(
            Runnable transition,
            Runnable persistence) {

        if (transition == null || persistence == null) {
            throw new IllegalArgumentException(
                    "La transición y la persistencia son obligatorias.");
        }

        ReturnStatus previousStatus = status;
        ReturnCondition previousCondition = condition;
        UUID previousWarehouseId = warehouseId;
        LocalDateTime previousReceivedAt = receivedAt;
        String previousRefundReference = refundReference;
        LocalDateTime previousRefundedAt = refundedAt;

        try {
            transition.run();
            persistence.run();
        } catch (RuntimeException exception) {
            status = previousStatus;
            condition = previousCondition;
            warehouseId = previousWarehouseId;
            receivedAt = previousReceivedAt;
            refundReference = previousRefundReference;
            refundedAt = previousRefundedAt;
            throw exception;
        }
    }

    private void requireStatus(ReturnStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "La devolución no se encuentra en el estado requerido: "
                            + expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public ReturnCondition getCondition() {
        return condition;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
