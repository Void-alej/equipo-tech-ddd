package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.InventoryMovementType;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.models.InventoryMovement;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.InventoryMovementRepository;
import com.nexusmarket.domain.ports.out.InventoryRepository;
import com.nexusmarket.domain.ports.out.ProductRepository;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.ports.out.WarehouseRepository;

import java.util.List;
import java.util.UUID;

public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            UserRepository userRepository) {

        if (inventoryRepository == null || movementRepository == null
                || productRepository == null || warehouseRepository == null
                || userRepository == null) {
            throw new IllegalArgumentException(
                    "Todos los repositorios son obligatorios.");
        }

        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.userRepository = userRepository;
    }

    public Inventory addStock(
            User actor, UUID productId, UUID warehouseId,
            int quantity, String description) {
        requireDescription(description);


        requirePositive(quantity);

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        authorizeOperation(actor, product, warehouse);
        requireActiveWarehouse(warehouse);
        requirePhysicalProduct(product);

        Inventory inventory = inventoryRepository
                .findByProductAndWarehouse(productId, warehouseId)
                .orElseGet(() -> new Inventory(
                        UUID.randomUUID(), productId, warehouseId, 0));

        inventory.addStock(quantity);
        inventoryRepository.save(inventory);

        recordMovement(inventory, InventoryMovementType.INCOME,
                quantity, actor, description);

        return inventory;
    }

    public Inventory reserveStock(
            User actor, UUID productId, UUID warehouseId,
            int quantity, String description) {
        requireDescription(description);


        requirePositive(quantity);

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        authorizeOperation(actor, product, warehouse);
        requireActiveWarehouse(warehouse);
        requirePhysicalProduct(product);

        if (!product.isAvailableForSale()) {
            throw new IllegalStateException(
                    "No se puede reservar un producto que no está publicado.");
        }

        Inventory inventory = findInventory(productId, warehouseId);
        inventory.reserve(quantity);
        inventoryRepository.save(inventory);

        recordMovement(inventory, InventoryMovementType.RESERVATION,
                quantity, actor, description);

        return inventory;
    }

    public Inventory releaseReservation(
            User actor, UUID productId, UUID warehouseId,
            int quantity, String description) {
        requireDescription(description);


        requirePositive(quantity);

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        authorizeOperation(actor, product, warehouse);
        requireActiveWarehouse(warehouse);

        Inventory inventory = findInventory(productId, warehouseId);
        inventory.releaseReservation(quantity);
        inventoryRepository.save(inventory);

        recordMovement(inventory,
                InventoryMovementType.RELEASE_RESERVATION,
                quantity, actor, description);

        return inventory;
    }

    public Inventory registerSale(
            User actor, UUID productId, UUID warehouseId,
            int quantity, String description) {
        requireDescription(description);


        requirePositive(quantity);

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        authorizeOperation(actor, product, warehouse);
        requireActiveWarehouse(warehouse);

        Inventory inventory = findInventory(productId, warehouseId);
        inventory.registerSale(quantity);
        inventoryRepository.save(inventory);

        recordMovement(inventory, InventoryMovementType.SALE,
                quantity, actor, description);

        return inventory;
    }

    public Inventory adjustStock(
            User actor, UUID productId, UUID warehouseId,
            int newQuantity, String description) {
        requireDescription(description);


        if (newQuantity < 0) {
            throw new IllegalArgumentException(
                    "La cantidad ajustada no puede ser negativa.");
        }

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        authorizeOperation(actor, product, warehouse);
        requireActiveWarehouse(warehouse);
        requirePhysicalProduct(product);

        Inventory inventory = findInventory(productId, warehouseId);
        int previousQuantity = inventory.getQuantity();

        inventory.adjustStock(newQuantity);
        inventoryRepository.save(inventory);

        int difference = Math.abs(newQuantity - previousQuantity);

        if (difference > 0) {
            recordMovement(inventory, InventoryMovementType.ADJUSTMENT,
                    difference, actor, description);
        }

        return inventory;
    }

    public Inventory registerReturn(
            User actor, UUID productId, UUID warehouseId,
            int quantity, String description) {
        requireDescription(description);


        requirePositive(quantity);

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        authorizeOperation(actor, product, warehouse);
        requireActiveWarehouse(warehouse);
        requirePhysicalProduct(product);

        Inventory inventory = inventoryRepository
                .findByProductAndWarehouse(productId, warehouseId)
                .orElseGet(() -> new Inventory(
                        UUID.randomUUID(), productId, warehouseId, 0));

        inventory.registerReturn(quantity);
        inventoryRepository.save(inventory);

        recordMovement(inventory, InventoryMovementType.RETURN,
                quantity, actor, description);

        return inventory;
    }

    public List<InventoryMovement> getMovementHistory(
            User actor, UUID productId, UUID warehouseId) {

        Product product = findProduct(productId);
        Warehouse warehouse = findWarehouse(warehouseId);

        requireActiveUser(actor);

        boolean authorized = actor.getRole() == UserRole.ADMINISTRATOR
                || actor.getRole() == UserRole.SUPERVISOR
                || actor.getRole() == UserRole.LOGISTICS_OPERATOR
                || (actor.getRole() == UserRole.SELLER
                    && actor.getId().equals(product.getSellerId())
                    && warehouse.getType() == WarehouseType.SELLER
                    && actor.getId().equals(warehouse.getSellerId()));

        if (!authorized) {
            throw new UnauthorizedUserOperationException(
                    "No tienes permisos para consultar este historial.");
        }

        return inventoryRepository
                .findByProductAndWarehouse(productId, warehouseId)
                .map(i -> movementRepository.findByInventoryId(i.getId()))
                .orElse(List.of());
    }

    private void authorizeOperation(
            User actor, Product product, Warehouse warehouse) {

        requireActiveUser(actor);

        if (actor.getRole() == UserRole.ADMINISTRATOR) {
            return;
        }

        if (actor.getRole() == UserRole.LOGISTICS_OPERATOR
                && warehouse.getType() == WarehouseType.MARKETPLACE) {
            return;
        }

        if (actor.getRole() == UserRole.SELLER
                && actor.getId().equals(product.getSellerId())
                && warehouse.getType() == WarehouseType.SELLER
                && actor.getId().equals(warehouse.getSellerId())) {
            return;
        }

        throw new UnauthorizedUserOperationException(
                "No tienes permisos para operar este inventario.");
    }

    private void recordMovement(
            Inventory inventory, InventoryMovementType type,
            int quantity, User actor, String description) {

        movementRepository.save(new InventoryMovement(
                UUID.randomUUID(), inventory.getId(), type,
                quantity, actor.getId(), description));
    }

    private void requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "La descripción del movimiento es obligatoria.");
        }
    }

    private Product findProduct(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "El identificador del producto es obligatorio.");
        }

        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el producto solicitado."));
    }

    private Warehouse findWarehouse(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "El identificador de la bodega es obligatorio.");
        }

        return warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la bodega solicitada."));
    }

    private Inventory findInventory(UUID productId, UUID warehouseId) {
        return inventoryRepository
                .findByProductAndWarehouse(productId, warehouseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe inventario para ese producto y esa bodega."));
    }

    private void requireActiveWarehouse(Warehouse warehouse) {
        if (!warehouse.isActive()) {
            throw new IllegalStateException(
                    "No se puede operar en una bodega inactiva.");
        }
    }

    private void requirePhysicalProduct(Product product) {
        if (product.getType() != ProductType.PHYSICAL) {
            throw new IllegalArgumentException(
                    "Los productos digitales no manejan inventario físico.");
        }
    }

    private void requireActiveUser(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "Se requiere un usuario activo.");
        }
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad debe ser mayor que cero.");
        }
    }
}
