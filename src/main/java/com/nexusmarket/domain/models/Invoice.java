package com.nexusmarket.domain.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class Invoice {

    private final UUID id;
    private final UUID orderId;
    private final UUID buyerId;

    private final String buyerName;
    private final String buyerEmail;
    private final String buyerDocument;

    private final List<InvoiceItem> items;
    private final BigDecimal total;
    private final LocalDateTime issuedAt;

    public Invoice(
            UUID id,
            UUID orderId,
            UUID buyerId,
            String buyerName,
            String buyerEmail,
            String buyerDocument,
            List<InvoiceItem> items,
            LocalDateTime issuedAt) {

        if (id == null || orderId == null || buyerId == null) {
            throw new IllegalArgumentException(
                    "Los identificadores de factura, pedido y comprador son obligatorios.");
        }
        if (buyerName == null || buyerName.isBlank()) {
            throw new IllegalArgumentException("El nombre del comprador es obligatorio.");
        }
        if (buyerEmail == null || buyerEmail.isBlank()) {
            throw new IllegalArgumentException("El correo del comprador es obligatorio.");
        }
        if (buyerDocument == null || buyerDocument.isBlank()) {
            throw new IllegalArgumentException("El documento del comprador es obligatorio.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "La factura debe contener al menos un producto.");
        }
        if (items.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException(
                    "La factura no puede contener productos nulos.");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("La fecha de emisión es obligatoria.");
        }

        this.id = id;
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.buyerName = buyerName.trim();
        this.buyerEmail = buyerEmail.trim();
        this.buyerDocument = buyerDocument.trim();
        this.items = List.copyOf(items);
        this.total = this.items.stream()
                .map(InvoiceItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.issuedAt = issuedAt;
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

    public String getBuyerName() {
        return buyerName;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public String getBuyerDocument() {
        return buyerDocument;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }
}
