package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.ReturnRequest;
import com.nexusmarket.domain.ports.out.ReturnRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryReturnRepository implements ReturnRepository {

    private final ConcurrentMap<UUID, ReturnRequest> requests =
            new ConcurrentHashMap<>();

    @Override
    public ReturnRequest save(ReturnRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "La solicitud de devolución es obligatoria.");
        }

        requests.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<ReturnRequest> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public List<ReturnRequest> findByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }

        return requests.values().stream()
                .filter(request -> request.getOrderId().equals(orderId))
                .sorted(Comparator.comparing(ReturnRequest::getRequestedAt))
                .toList();
    }

    @Override
    public List<ReturnRequest> findByBuyerId(UUID buyerId) {
        if (buyerId == null) {
            return List.of();
        }

        return requests.values().stream()
                .filter(request -> request.getBuyerId().equals(buyerId))
                .sorted(Comparator.comparing(ReturnRequest::getRequestedAt))
                .toList();
    }

    @Override
    public List<ReturnRequest> findAll() {
        return requests.values().stream()
                .sorted(Comparator.comparing(ReturnRequest::getRequestedAt))
                .toList();
    }
}
