package com.equipotech.domain.ports.out;

import com.equipotech.domain.models.Rental;

import java.util.Optional;
import java.util.UUID;

public interface RentalRepository {

    Rental save(Rental rental);

    Optional<Rental> findById(UUID id);
}