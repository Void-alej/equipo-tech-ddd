package com.equipotech.domain.ports.out;

import com.equipotech.domain.models.Equipment;

import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository {

    Equipment save(Equipment equipment);

    Optional<Equipment> findById(UUID id);
}