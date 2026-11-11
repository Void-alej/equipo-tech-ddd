package com.nexusmarket.application.services;

import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;

public record SellerOnboardingResult(
        User seller,
        Warehouse firstWarehouse) {

    public SellerOnboardingResult {
        if (seller == null || firstWarehouse == null) {
            throw new IllegalArgumentException(
                    "El vendedor y su primera bodega son obligatorios.");
        }
    }
}
