package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.BuyerProfile;

import java.util.Optional;
import java.util.UUID;

public interface BuyerProfileRepository {

    void save(BuyerProfile profile);

    Optional<BuyerProfile> findByBuyerId(UUID buyerId);
}
