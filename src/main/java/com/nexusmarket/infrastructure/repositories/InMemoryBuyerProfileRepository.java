package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.BuyerProfile;
import com.nexusmarket.domain.ports.out.BuyerProfileRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryBuyerProfileRepository implements BuyerProfileRepository {

    private final ConcurrentMap<UUID, BuyerProfile> profiles =
            new ConcurrentHashMap<>();

    @Override
    public void save(BuyerProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException(
                    "El perfil del comprador no puede ser nulo.");
        }

        profiles.put(profile.getBuyerId(), profile);
    }

    @Override
    public Optional<BuyerProfile> findByBuyerId(UUID buyerId) {
        if (buyerId == null) {
            throw new IllegalArgumentException(
                    "El identificador del comprador es obligatorio.");
        }

        return Optional.ofNullable(profiles.get(buyerId));
    }
}
