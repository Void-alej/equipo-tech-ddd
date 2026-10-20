package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.util.List;
import java.util.UUID;

public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    public WarehouseService(
            WarehouseRepository warehouseRepository,
            UserRepository userRepository) {
        if (warehouseRepository == null || userRepository == null) {
            throw new IllegalArgumentException(
                    "Los repositorios de bodegas y usuarios son obligatorios.");
        }
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
    }

    public Warehouse createMarketplaceWarehouse(
            User actor, String name, String address) {
        requireAdministrator(actor);

        Warehouse warehouse = new Warehouse(
                UUID.randomUUID(), name, address,
                WarehouseType.MARKETPLACE, null);

        return warehouseRepository.save(warehouse);
    }

    public Warehouse createSellerWarehouse(
            User actor, UUID sellerId, String name, String address) {
        requireActiveUser(actor);

        if (sellerId == null) {
            throw new IllegalArgumentException(
                    "El identificador del vendedor es obligatorio.");
        }

        if (actor.getRole() == UserRole.ADMINISTRATOR) {
            User seller = userRepository.findById(sellerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe el vendedor indicado."));

            if (!seller.isActive() || seller.getRole() != UserRole.SELLER) {
                throw new IllegalArgumentException(
                        "La bodega debe pertenecer a un vendedor activo.");
            }
        } else if (actor.getRole() != UserRole.SELLER
                || !actor.getId().equals(sellerId)) {
            throw new UnauthorizedUserOperationException(
                    "Solo un administrador o el propio vendedor puede crear esta bodega.");
        }

        Warehouse warehouse = new Warehouse(
                UUID.randomUUID(), name, address,
                WarehouseType.SELLER, sellerId);

        return warehouseRepository.save(warehouse);
    }

    public Warehouse deactivateWarehouse(User actor, UUID warehouseId) {
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        authorizeWarehouseManagement(actor, warehouse);

        warehouse.deactivate();
        return warehouseRepository.save(warehouse);
    }

    public Warehouse activateWarehouse(User actor, UUID warehouseId) {
        Warehouse warehouse = findWarehouseOrThrow(warehouseId);
        authorizeWarehouseManagement(actor, warehouse);

        warehouse.activate();
        return warehouseRepository.save(warehouse);
    }

    public List<Warehouse> getAllWarehouses(User actor) {
        requireActiveUser(actor);

        if (actor.getRole() != UserRole.ADMINISTRATOR
                && actor.getRole() != UserRole.SUPERVISOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo administradores y supervisores pueden consultar todas las bodegas.");
        }

        return warehouseRepository.findAll();
    }

    public List<Warehouse> getMyWarehouses(User actor) {
        requireActiveUser(actor);

        if (actor.getRole() != UserRole.SELLER) {
            throw new UnauthorizedUserOperationException(
                    "Esta consulta está disponible para vendedores.");
        }

        return warehouseRepository.findBySellerId(actor.getId());
    }

    private Warehouse findWarehouseOrThrow(UUID warehouseId) {
        if (warehouseId == null) {
            throw new IllegalArgumentException(
                    "El identificador de la bodega es obligatorio.");
        }

        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la bodega solicitada."));
    }

    private void authorizeWarehouseManagement(
            User actor, Warehouse warehouse) {
        requireActiveUser(actor);

        if (actor.getRole() == UserRole.ADMINISTRATOR) {
            return;
        }

        if (actor.getRole() == UserRole.SELLER
                && warehouse.getType() == WarehouseType.SELLER
                && actor.getId().equals(warehouse.getSellerId())) {
            return;
        }

        throw new UnauthorizedUserOperationException(
                "No tienes permisos para modificar esta bodega.");
    }

    private void requireAdministrator(User actor) {
        requireActiveUser(actor);

        if (actor.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo un administrador activo puede realizar esta operación.");
        }
    }

    private void requireActiveUser(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "Se requiere un usuario activo para realizar esta operación.");
        }
    }
}
