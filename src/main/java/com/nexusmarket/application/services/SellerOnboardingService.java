package com.nexusmarket.application.services;

import java.util.Objects;

import com.nexusmarket.application.exceptions.SellerOnboardingException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.models.Warehouse;

public class SellerOnboardingService {

    private final UserService userService;
    private final WarehouseService warehouseService;

    public SellerOnboardingService(
            UserService userService,
            WarehouseService warehouseService) {

        this.userService = Objects.requireNonNull(
                userService, "UserService es obligatorio.");

        this.warehouseService = Objects.requireNonNull(
                warehouseService, "WarehouseService es obligatorio.");
    }

    public SellerOnboardingResult registerSellerWithFirstWarehouse(
            User administrator,
            String fullName,
            String email,
            String documentId,
            String warehouseName,
            String warehouseAddress) {

        User seller = userService.registerUserByAdministrator(
                administrator,
                fullName,
                email,
                documentId,
                UserRole.SELLER
        );

        Warehouse firstWarehouse;

        try {
            firstWarehouse = warehouseService.createSellerWarehouse(
                    administrator,
                    seller.getId(),
                    warehouseName,
                    warehouseAddress
            );
        } catch (RuntimeException exception) {
            throw new SellerOnboardingException(seller, exception);
        }

        return new SellerOnboardingResult(seller, firstWarehouse);
    }
}
