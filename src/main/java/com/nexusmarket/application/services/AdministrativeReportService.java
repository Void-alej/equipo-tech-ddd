package com.nexusmarket.application.services;

import com.nexusmarket.application.dto.AdministrativeReport;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;
import com.nexusmarket.domain.ports.out.InventoryRepository;
import com.nexusmarket.domain.ports.out.InventoryMovementRepository;
import com.nexusmarket.domain.ports.out.OrderRepository;
import com.nexusmarket.domain.ports.out.InvoiceRepository;
import com.nexusmarket.domain.ports.out.ShipmentRepository;
import com.nexusmarket.domain.ports.out.ReturnRepository;

import java.time.LocalDateTime;

public class AdministrativeReportService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRepository returnRepository;

    public AdministrativeReportService(
            UserRepository userRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            OrderRepository orderRepository,
            InvoiceRepository invoiceRepository,
            ShipmentRepository shipmentRepository,
            ReturnRepository returnRepository) {

        if (userRepository == null || productRepository == null
                || warehouseRepository == null || inventoryRepository == null
                || movementRepository == null || orderRepository == null
                || invoiceRepository == null || shipmentRepository == null
                || returnRepository == null) {
            throw new IllegalArgumentException(
                    "Todos los repositorios del reporte son obligatorios.");
        }

        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRepository = returnRepository;
    }

    public AdministrativeReport generateReport(User actor) {
        User registeredUser = findActiveRegisteredUser(actor);

        if (registeredUser.getRole() != UserRole.ADMINISTRATOR
                && registeredUser.getRole() != UserRole.SUPERVISOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo administración o supervisión puede consultar "
                            + "el reporte administrativo.");
        }

        return new AdministrativeReport(
                LocalDateTime.now(),
                userRepository.findAll(),
                productRepository.findAll(),
                warehouseRepository.findAll(),
                inventoryRepository.findAll(),
                movementRepository.findAll(),
                orderRepository.findAll(),
                invoiceRepository.findAll(),
                shipmentRepository.findAll(),
                returnRepository.findAll()
        );
    }

    private User findActiveRegisteredUser(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "Se requiere un usuario activo.");
        }

        User registered = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedUserOperationException(
                        "El usuario no está registrado."));

        if (!registered.isActive()
                || registered.getRole() != actor.getRole()) {
            throw new UnauthorizedUserOperationException(
                    "El usuario no está activo o sus permisos no coinciden.");
        }

        return registered;
    }
}
