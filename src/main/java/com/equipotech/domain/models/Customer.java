package com.equipotech.domain.models;

import com.equipotech.domain.valueobjects.Email;
import com.equipotech.domain.valueobjects.Phone;

import java.util.UUID;

public class Customer {

    private final UUID id;
    private final String name;
    private final Email email;
    private final Phone phone;

    public Customer(
            String name,
            Email email,
            Phone phone
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre es obligatorio"
            );
        }

        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public Phone getPhone() {
        return phone;
    }
}