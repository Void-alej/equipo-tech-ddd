package com.nexusmarket.application.dto;

import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.models.Inventory;
import com.nexusmarket.domain.models.InventoryMovement;
import com.nexusmarket.domain.models.Order;
import com.nexusmarket.domain.models.Invoice;
import com.nexusmarket.domain.models.Shipment;
import com.nexusmarket.domain.models.ReturnRequest;

import java.time.LocalDateTime;
import java.util.List;

public record AdministrativeReport(
        LocalDateTime generatedAt,
        List<User> users,
        List<Product> products,
        List<Warehouse> warehouses,
        List<Inventory> inventories,
        List<InventoryMovement> inventoryMovements,
        List<Order> orders,
        List<Invoice> invoices,
        List<Shipment> shipments,
        List<ReturnRequest> returnRequests
) {
    public AdministrativeReport {
        if (generatedAt == null) {
            throw new IllegalArgumentException(
                    "La fecha de generación del reporte es obligatoria.");
        }

        if (users == null || products == null || warehouses == null
                || inventories == null || inventoryMovements == null
                || orders == null || invoices == null || shipments == null
                || returnRequests == null) {
            throw new IllegalArgumentException(
                    "Las colecciones del reporte son obligatorias.");
        }

        users = List.copyOf(users);
        products = List.copyOf(products);
        warehouses = List.copyOf(warehouses);
        inventories = List.copyOf(inventories);
        inventoryMovements = List.copyOf(inventoryMovements);
        orders = List.copyOf(orders);
        invoices = List.copyOf(invoices);
        shipments = List.copyOf(shipments);
        returnRequests = List.copyOf(returnRequests);
    }
}
