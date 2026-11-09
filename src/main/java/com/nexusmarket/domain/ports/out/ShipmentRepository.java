package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.Shipment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    void save(Shipment shipment);

    void saveAll(List<Shipment> shipments);

    Optional<Shipment> findById(UUID id);

    List<Shipment> findByOrderId(UUID orderId);

    List<Shipment> findAll();
}
