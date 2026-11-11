package com.nexusmarket.application.exceptions;

import com.nexusmarket.domain.models.User;

public class SellerOnboardingException extends RuntimeException {

    private final User registeredSeller;

    public SellerOnboardingException(
            User registeredSeller,
            Throwable cause) {

        super(
                "El vendedor fue registrado, pero no se pudo crear su primera bodega.",
                cause
        );

        if (registeredSeller == null) {
            throw new IllegalArgumentException(
                    "El vendedor registrado es obligatorio.");
        }

        this.registeredSeller = registeredSeller;
    }

    public User getRegisteredSeller() {
        return registeredSeller;
    }
}
