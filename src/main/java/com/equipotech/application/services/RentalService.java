package com.equipotech.application.services;

import com.equipotech.domain.models.Rental;
import com.equipotech.domain.ports.out.RentalRepository;

import java.util.UUID;

public class RentalService {

    private final RentalRepository repository;

    public RentalService(RentalRepository repository) {
        this.repository = repository;
    }

    public Rental create(Rental rental) {

        return repository.save(rental);
    }

    public Rental find(UUID id) {

        return repository.findById(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Alquiler no encontrado"
                        )
                );
    }
}