package com.equipotech.domain.valueobjects;

import java.util.Objects;

public record Email(String value) {

    public Email {

        Objects.requireNonNull(
                value,
                "El correo electrónico es obligatorio"
        );

        if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException(
                    "Correo electrónico inválido"
            );
        }
    }
}