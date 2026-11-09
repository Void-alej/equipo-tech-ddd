package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    void save(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    Optional<Invoice> findByOrderId(UUID orderId);

    List<Invoice> findByBuyerId(UUID buyerId);

    List<Invoice> findAll();
}
