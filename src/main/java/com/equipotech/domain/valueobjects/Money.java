package com.equipotech.domain.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public Money {

        Objects.requireNonNull(
                amount,
                "El valor monetario es obligatorio"
        );

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "El valor no puede ser negativo"
            );
        }
    }

    public Money(double amount) {
        this(BigDecimal.valueOf(amount));
    }

    public Money multiply(long factor) {

        return new Money(
                amount.multiply(
                        BigDecimal.valueOf(factor)
                )
        );
    }

    public Money add(Money other) {

        return new Money(
                amount.add(other.amount)
        );
    }
}