package com.equipotech.domain.ports.out;

import com.equipotech.domain.models.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);
}