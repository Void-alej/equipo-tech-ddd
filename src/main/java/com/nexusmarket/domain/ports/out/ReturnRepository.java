package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.ReturnRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnRepository {

    ReturnRequest save(ReturnRequest request);

    Optional<ReturnRequest> findById(UUID id);

    List<ReturnRequest> findByOrderId(UUID orderId);

    List<ReturnRequest> findByBuyerId(UUID buyerId);

    List<ReturnRequest> findAll();
}
