package com.equipotech.infrastructure.repositories;

import com.equipotech.domain.models.Rental;
import com.equipotech.domain.ports.out.RentalRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryRentalRepository
        implements RentalRepository {

    private final Map<UUID, Rental> rentals =
            new HashMap<>();

    @Override
    public Rental save(Rental rental) {

        rentals.put(
                rental.getId(),
                rental
        );

        return rental;
    }

    @Override
    public Optional<Rental> findById(UUID id) {

        return Optional.ofNullable(
                rentals.get(id)
        );
    }
}