package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.Invoice;
import com.nexusmarket.domain.ports.out.InvoiceRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final Map<UUID, Invoice> invoicesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> invoiceIdByOrderId = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(Invoice invoice) {
        if (invoice == null) {
            throw new IllegalArgumentException("La factura es obligatoria.");
        }

        if (invoicesById.containsKey(invoice.getId())) {
            throw new IllegalStateException(
                    "Ya existe una factura con ese identificador.");
        }

        if (invoiceIdByOrderId.containsKey(invoice.getOrderId())) {
            throw new IllegalStateException(
                    "El pedido ya tiene una factura emitida.");
        }

        invoicesById.put(invoice.getId(), invoice);
        invoiceIdByOrderId.put(invoice.getOrderId(), invoice.getId());
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return Optional.ofNullable(invoicesById.get(id));
    }

    @Override
    public Optional<Invoice> findByOrderId(UUID orderId) {
        UUID invoiceId = invoiceIdByOrderId.get(orderId);
        return invoiceId == null
                ? Optional.empty()
                : findById(invoiceId);
    }

    @Override
    public List<Invoice> findByBuyerId(UUID buyerId) {
        return invoicesById.values().stream()
                .filter(invoice -> invoice.getBuyerId().equals(buyerId))
                .sorted(Comparator.comparing(Invoice::getIssuedAt))
                .toList();
    }

    @Override
    public List<Invoice> findAll() {
        return invoicesById.values().stream()
                .sorted(Comparator.comparing(Invoice::getIssuedAt))
                .toList();
    }
}
