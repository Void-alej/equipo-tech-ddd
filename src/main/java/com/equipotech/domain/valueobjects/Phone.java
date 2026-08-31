package com.equipotech.domain.valueobjects;

import java.util.Objects;

public record Phone(String value) {

    public Phone {

        Objects.requireNonNull(
                value,
                "El teléfono es obligatorio"
        );

        if (!value.matches("\\+?[0-9 ]{7,15}")) {
            throw new IllegalArgumentException(
                    "Número de teléfono inválido"
            );
        }
    }
}