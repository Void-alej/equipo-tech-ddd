package com.nexusmarket.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.nexusmarket.application.dto.SellerOrderView;
import com.nexusmarket.domain.enums.*;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.*;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API HTTP y punto de entrada para la interfaz de demostración.
 * Cada operación protegida resuelve el usuario desde un token de sesión
 * y vuelve a leer sus permisos desde el repositorio de usuarios.
 */
public final class NexusMarketHttpServer {

    private static final int MAX_BODY_BYTES = 1_048_576;

    private final NexusMarketRuntime runtime;
    private final ObjectMapper json;
    private final HttpServer server;
    private final ExecutorService executor;

    public NexusMarketHttpServer(NexusMarketRuntime runtime, int port)
            throws IOException {
        this.runtime = Objects.requireNonNull(runtime);
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::handle);
        this.executor = Executors.newFixedThreadPool(8);
        this.server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set(
                        "Access-Control-Allow-Headers", "Authorization, Content-Type");
                exchange.getResponseHeaders().set(
                        "Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("/".equals(path) || "/index.html".equals(path)) {
                serveIndex(exchange);
                return;
            }

            if ("/api/health".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, Map.of("status", "UP", "application", "NexusMarket"));
                return;
            }

            if ("/api/auth/login".equals(path) && "POST".equals(method)) {
                JsonNode body = readBody(exchange);
                sendJson(exchange, 200, runtime.authenticationService.login(
                        text(body, "email"), text(body, "password")));
                return;
            }

            String token = bearerToken(exchange);
            if ("/api/auth/logout".equals(path) && "POST".equals(method)) {
                runtime.authenticationService.logout(token);
                sendJson(exchange, 200, Map.of("message", "Sesión cerrada."));
                return;
            }

            User actor = runtime.authenticationService.requireSession(token);
            String[] p = Arrays.stream(path.substring(4).split("/"))
                    .filter(part -> !part.isBlank()).toArray(String[]::new);

            Object result = route(method, p, actor, exchange);
            if (result == NoContent.INSTANCE) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                sendJson(exchange, 200, result);
            }
        } catch (UnauthorizedUserOperationException exception) {
            sendError(exchange, 403, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IllegalStateException exception) {
            sendError(exchange, 409, exception.getMessage());
        } catch (Exception exception) {
            sendError(exchange, 500,
                    exception.getMessage() == null
                            ? "Error interno del servidor." : exception.getMessage());
        } finally {
            exchange.close();
        }
    }

    private Object route(
            String method, String[] p, User actor, HttpExchange exchange)
            throws IOException {

        JsonNode body = "POST".equals(method) ? readBody(exchange) : json.createObjectNode();

        if ("GET".equals(method)) {
            if (p.length == 1 && "me".equals(p[0])) return userView(actor);
            if (p.length == 1 && "users".equals(p[0])) {
                return runtime.userService.getAllUsers(actor).stream()
                        .map(this::userView).toList();
            }
            if (p.length == 2 && "buyer-profile".equals(p[0])
                    && "me".equals(p[1])) {
                return runtime.buyerService.getMyProfile(actor);
            }
            if (p.length == 1 && "products".equals(p[0])) {
                return productsFor(actor);
            }
            if (p.length == 2 && "products".equals(p[0])
                    && "mine".equals(p[1])) {
                return runtime.productService.getMyProducts(actor);
            }
            if (p.length == 1 && "warehouses".equals(p[0])) {
                return warehousesFor(actor);
            }
            if (p.length == 1 && "inventory".equals(p[0])) {
                return inventoryFor(actor);
            }
            if (p.length == 1 && "orders".equals(p[0])) {
                return ordersFor(actor);
            }
            if (p.length == 1 && "shipments".equals(p[0])) {
                return shipmentsFor(actor);
            }
            if (p.length == 1 && "returns".equals(p[0])) {
                return returnsFor(actor);
            }
            if (p.length == 1 && "invoices".equals(p[0])) {
                return runtime.invoiceService.getMyInvoices(actor);
            }
            if (p.length == 2 && "invoices".equals(p[0])) {
                return runtime.invoiceService.getMyInvoice(actor, uuid(p[1]));
            }
            if (p.length == 2 && "carts".equals(p[0])) {
                return runtime.cartService.getCart(actor, uuid(p[1]));
            }
            if (p.length == 2 && "orders".equals(p[0])) {
                return orderFor(actor, uuid(p[1]));
            }
            if (p.length == 3 && "orders".equals(p[0])
                    && "shipments".equals(p[2])) {
                return runtime.shipmentService.getOrderShipments(actor, uuid(p[1]));
            }
            if (p.length == 3 && "buyers".equals(p[0])
                    && "profile".equals(p[2])) {
                return runtime.buyerService.getProfileByAdministrator(
                        actor, uuid(p[1]));
            }
            if (p.length == 2 && "returns".equals(p[0])) {
                return runtime.returnService.getReturn(actor, uuid(p[1]));
            }
            if (p.length == 2 && "reports".equals(p[0])
                    && "admin".equals(p[1])) {
                return runtime.administrativeReportService.generateReport(actor);
            }
            if (p.length == 2 && "reports".equals(p[0])
                    && "seller".equals(p[1])) {
                return sellerReport(actor);
            }
            throw new IllegalArgumentException("Endpoint GET no encontrado.");
        }

        if (!"POST".equals(method)) {
            throw new IllegalArgumentException("Método HTTP no permitido.");
        }

        if (p.length == 1 && "buyers".equals(p[0])) {
            requireRole(actor, UserRole.ADMINISTRATOR);
            User buyer = runtime.userService.registerBuyer(
                    text(body, "fullName"), text(body, "email"), text(body, "documentId"));
            runtime.authenticationService.setPassword(buyer, text(body, "password"));
            runtime.buyerService.createMyProfile(
                    buyer, text(body, "primaryAddress"), stringList(body.path("additionalAddresses")));
            return Map.of("user", userView(buyer),
                    "profile", runtime.buyerService.getMyProfile(buyer));
        }

        if (p.length == 1 && "sellers".equals(p[0])) {
            requireRole(actor, UserRole.ADMINISTRATOR);
            User seller;
            try {
                var onboarding = runtime.sellerOnboardingService
                        .registerSellerWithFirstWarehouse(
                                actor, text(body, "fullName"), text(body, "email"),
                                text(body, "documentId"), text(body, "warehouseName"),
                                text(body, "warehouseAddress"));
                seller = onboarding.seller();
                runtime.authenticationService.setPassword(seller, text(body, "password"));
                return Map.of("seller", userView(seller),
                        "firstWarehouse", onboarding.firstWarehouse());
            } catch (com.nexusmarket.application.exceptions.SellerOnboardingException exception) {
                runtime.authenticationService.setPassword(
                        exception.getRegisteredSeller(), text(body, "password"));
                throw exception;
            }
        }

        if (p.length == 1 && "logistics-operators".equals(p[0])) {
            requireRole(actor, UserRole.ADMINISTRATOR);
            User operator = runtime.userService.registerUserByAdministrator(
                    actor, text(body, "fullName"), text(body, "email"),
                    text(body, "documentId"), UserRole.LOGISTICS_OPERATOR);
            runtime.authenticationService.setPassword(operator, text(body, "password"));
            return userView(operator);
        }

        if (p.length == 1 && "products".equals(p[0])) {
            requireRole(actor, UserRole.SELLER);
            Product product = runtime.productService.createProduct(
                    actor, text(body, "name"), text(body, "description"),
                    ProductType.valueOf(text(body, "type")),
                    new BigDecimal(text(body, "price")));
            return product;
        }

        if (p.length == 2 && "products".equals(p[0])
                && "publish".equals(p[1])) {
            throw new IllegalArgumentException(
                    "Usa POST /api/products/{id}/publish.");
        }

        if (p.length == 3 && "products".equals(p[0])
                && "publish".equals(p[2])) {
            return runtime.productService.publishProduct(actor, uuid(p[1]));
        }

        if (p.length == 1 && "inventory".equals(p[0])) {
            requireOneOf(actor, UserRole.SELLER, UserRole.LOGISTICS_OPERATOR);
            return runtime.inventoryService.addStock(
                    actor, uuid(text(body, "productId")),
                    uuid(text(body, "warehouseId")), integer(body, "quantity"),
                    optionalText(body, "description", "Ingreso inicial de inventario"));
        }

        if (p.length == 1 && "carts".equals(p[0])) {
            return runtime.cartService.createCart(actor);
        }

        if (p.length == 3 && "carts".equals(p[0])
                && "items".equals(p[2])) {
            return addCartItem(actor, uuid(p[1]), body);
        }

        if (p.length == 1 && "orders".equals(p[0])) {
            return runtime.orderService.createOrderFromCart(
                    actor, uuid(text(body, "cartId")));
        }

        if (p.length == 3 && "orders".equals(p[0]) && "pay".equals(p[2])) {
            return runtime.orderService.registerPayment(
                    actor, uuid(p[1]), body.path("approved").asBoolean(false));
        }

        if (p.length == 3 && "orders".equals(p[0]) && "cancel".equals(p[2])) {
            return runtime.orderService.cancelOrder(actor, uuid(p[1]));
        }

        if (p.length == 3 && "orders".equals(p[0]) && "invoice".equals(p[2])) {
            return runtime.invoiceService.issueInvoice(actor, uuid(p[1]));
        }

        if (p.length == 3 && "orders".equals(p[0])
                && "shipments".equals(p[2])) {
            return runtime.shipmentService.createShipments(actor, uuid(p[1]));
        }

        if (p.length == 3 && "orders".equals(p[0]) && "returns".equals(p[2])) {
            return runtime.returnService.requestReturn(
                    actor, uuid(p[1]), uuid(text(body, "productId")),
                    integer(body, "quantity"), text(body, "reason"));
        }

        if (p.length == 3 && "shipments".equals(p[0])) {
            UUID shipmentId = uuid(p[1]);
            String description = optionalText(body, "description",
                    "Actualización registrada desde NexusMarket");
            return switch (p[2]) {
                case "pack" -> runtime.shipmentService.packShipment(actor, shipmentId, description);
                case "dispatch" -> runtime.shipmentService.dispatchShipment(actor, shipmentId, description);
                case "transit" -> runtime.shipmentService.markShipmentInTransit(actor, shipmentId, description);
                case "deliver" -> runtime.shipmentService.deliverShipment(actor, shipmentId, description);
                default -> throw new IllegalArgumentException("Transición logística desconocida.");
            };
        }

        if (p.length == 3 && "returns".equals(p[0])) {
            UUID returnId = uuid(p[1]);
            return switch (p[2]) {
                case "approve" -> runtime.returnService.approveReturn(actor, returnId);
                case "reject" -> runtime.returnService.rejectReturn(actor, returnId);
                case "receive" -> runtime.returnService.registerPhysicalReceipt(
                        actor, returnId, uuid(text(body, "warehouseId")),
                        ReturnCondition.valueOf(text(body, "condition")));
                case "refund" -> runtime.returnService.markRefunded(
                        actor, returnId, text(body, "refundReference"));
                default -> throw new IllegalArgumentException("Transición de devolución desconocida.");
            };
        }

        throw new IllegalArgumentException("Endpoint POST no encontrado.");
    }

    private Object addCartItem(User actor, UUID cartId, JsonNode body) {
        UUID productId = uuid(text(body, "productId"));
        int quantity = integer(body, "quantity");
        Product product = runtime.products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el producto."));
        if (product.getType() == ProductType.PHYSICAL) {
            int available = runtime.inventories.findByProductId(productId).stream()
                    .filter(inventory -> runtime.warehouses.findById(inventory.getWarehouseId())
                            .map(warehouse -> warehouse.isActive()
                                    && warehouse.getType() == WarehouseType.MARKETPLACE)
                            .orElse(false))
                    .mapToInt(Inventory::getAvailableQuantity).sum();
            int alreadyInCart = runtime.cartService.getItems(actor, cartId).stream()
                    .filter(item -> item.getProductId().equals(productId))
                    .mapToInt(CartItem::getQuantity).sum();
            if (quantity > available - alreadyInCart) {
                throw new IllegalStateException(
                        "Inventario insuficiente. Disponible para agregar: "
                                + Math.max(0, available - alreadyInCart));
            }
        }
        return runtime.cartService.addProduct(actor, cartId, productId, quantity);
    }

    private Object productsFor(User actor) {
        return actor.getRole() == UserRole.SELLER
                ? runtime.productService.getMyProducts(actor)
                : runtime.productService.getPublishedProducts();
    }

    private Object warehousesFor(User actor) {
        return switch (actor.getRole()) {
            case ADMINISTRATOR, SUPERVISOR -> runtime.warehouseService.getAllWarehouses(actor);
            case SELLER -> runtime.warehouseService.getMyWarehouses(actor);
            case LOGISTICS_OPERATOR -> runtime.warehouses.findAll();
            default -> throw new UnauthorizedUserOperationException(
                    "El comprador no puede consultar la administración de bodegas.");
        };
    }

    private Object inventoryFor(User actor) {
        if (actor.getRole() == UserRole.BUYER) {
            throw new UnauthorizedUserOperationException(
                    "El comprador no puede consultar ni administrar inventario.");
        }
        if (actor.getRole() == UserRole.SELLER) {
            Set<UUID> ids = runtime.products.findBySellerId(actor.getId()).stream()
                    .map(Product::getId).collect(Collectors.toSet());
            return runtime.inventories.findAll().stream()
                    .filter(item -> ids.contains(item.getProductId())).toList();
        }
        if (actor.getRole() == UserRole.LOGISTICS_OPERATOR
                || actor.getRole() == UserRole.ADMINISTRATOR
                || actor.getRole() == UserRole.SUPERVISOR) {
            return runtime.inventories.findAll();
        }
        throw new UnauthorizedUserOperationException("Sin permiso para consultar inventario.");
    }

    private Object ordersFor(User actor) {
        return switch (actor.getRole()) {
            case BUYER -> runtime.orders.findByBuyerId(actor.getId());
            case SELLER -> runtime.orderService.getMySellerOrders(actor);
            case ADMINISTRATOR, SUPERVISOR, LOGISTICS_OPERATOR -> runtime.orders.findAll();
        };
    }

    private Object shipmentsFor(User actor) {
        if (actor.getRole() == UserRole.BUYER) {
            Set<UUID> orderIds = runtime.orders.findByBuyerId(actor.getId()).stream()
                    .map(Order::getId).collect(Collectors.toSet());
            return runtime.shipments.findAll().stream()
                    .filter(shipment -> orderIds.contains(shipment.getOrderId())).toList();
        }
        if (actor.getRole() == UserRole.SELLER) {
            throw new UnauthorizedUserOperationException(
                    "La consulta de envíos por vendedor no está habilitada.");
        }
        return runtime.shipments.findAll();
    }

    private Object returnsFor(User actor) {
        if (actor.getRole() == UserRole.BUYER) {
            return runtime.returnService.getMyReturns(actor);
        }
        if (actor.getRole() == UserRole.ADMINISTRATOR) {
            return runtime.returnService.getAllReturns(actor);
        }
        if (actor.getRole() == UserRole.SELLER) {
            Set<UUID> products = runtime.products.findBySellerId(actor.getId()).stream()
                    .map(Product::getId).collect(Collectors.toSet());
            return runtime.returns.findAll().stream()
                    .filter(request -> products.contains(request.getProductId())).toList();
        }
        throw new UnauthorizedUserOperationException(
                "El rol no tiene permiso para consultar devoluciones.");
    }

    private Object orderFor(User actor, UUID orderId) {
        Order order = runtime.orders.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido."));
        if (actor.getRole() == UserRole.BUYER && !order.getBuyerId().equals(actor.getId())) {
            throw new UnauthorizedUserOperationException("No puedes consultar ese pedido.");
        }
        if (actor.getRole() == UserRole.SELLER) {
            return runtime.orderService.getMySellerOrders(actor).stream()
                    .filter(view -> view.orderId().equals(orderId))
                    .findFirst()
                    .orElseThrow(() -> new UnauthorizedUserOperationException(
                            "El pedido no contiene productos de este vendedor."));
        }
        return order;
    }

    private Object sellerReport(User actor) {
        requireRole(actor, UserRole.SELLER);
        List<Product> sellerProducts = runtime.products.findBySellerId(actor.getId()).stream()
                .sorted(Comparator.comparing(product -> product.getId().toString()))
                .toList();
        Set<UUID> productIds = sellerProducts.stream()
                .map(Product::getId).collect(Collectors.toSet());
        List<Inventory> sellerInventory = runtime.inventories.findAll().stream()
                .filter(item -> productIds.contains(item.getProductId()))
                .sorted(Comparator.comparing((Inventory item) -> item.getProductId().toString())
                        .thenComparing(item -> item.getWarehouseId().toString()))
                .toList();
        Set<UUID> inventoryIds = sellerInventory.stream()
                .map(Inventory::getId).collect(Collectors.toSet());
        List<Order> sellerOrders = runtime.orders.findAll().stream()
                .filter(order -> order.getItems().stream()
                        .anyMatch(item -> productIds.contains(item.getProductId())))
                .sorted(Comparator.comparing(order -> order.getId().toString()))
                .toList();
        List<ReturnRequest> sellerReturns = runtime.returns.findAll().stream()
                .filter(request -> productIds.contains(request.getProductId()))
                .sorted(Comparator.comparing(request -> request.getId().toString()))
                .toList();

        List<Map<String, Object>> sales = sellerProducts.stream().map(product -> {
            int sold = sellerOrders.stream()
                    .filter(order -> order.getPaymentStatus() == PaymentStatus.APPROVED)
                    .flatMap(order -> order.getItems().stream())
                    .filter(item -> item.getProductId().equals(product.getId()))
                    .mapToInt(OrderItem::getQuantity).sum();

            List<ReturnRequest> productReturns = sellerReturns.stream()
                    .filter(request -> request.getProductId().equals(product.getId()))
                    .toList();

            int unitsInReturnProcess = productReturns.stream()
                    .filter(request -> request.getStatus() == ReturnStatus.REQUESTED
                            || request.getStatus() == ReturnStatus.APPROVED
                            || request.getStatus() == ReturnStatus.REFUND_PENDING)
                    .mapToInt(ReturnRequest::getQuantity).sum();

            int unitsRefunded = productReturns.stream()
                    .filter(request -> request.getStatus() == ReturnStatus.REFUNDED)
                    .mapToInt(ReturnRequest::getQuantity).sum();

            BigDecimal gross = sellerOrders.stream()
                    .filter(order -> order.getPaymentStatus() == PaymentStatus.APPROVED)
                    .flatMap(order -> order.getItems().stream())
                    .filter(item -> item.getProductId().equals(product.getId()))
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal refundedAmount = productReturns.stream()
                    .filter(request -> request.getStatus() == ReturnStatus.REFUNDED)
                    .map(ReturnRequest::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return Map.<String, Object>of(
                    "productId", product.getId(),
                    "productName", product.getName(),
                    "unitsSold", sold,
                    "unitsInReturnProcess", unitsInReturnProcess,
                    "unitsRefunded", unitsRefunded,
                    "grossSales", gross,
                    "refundedAmount", refundedAmount,
                    "netSalesAfterRefunds", gross.subtract(refundedAmount));
        }).toList();

        return Map.of(
                "generatedAt", java.time.LocalDateTime.now(),
                "seller", userView(actor),
                "products", sellerProducts,
                "inventory", sellerInventory,
                "availableInventory", sellerInventory.stream()
                        .mapToInt(Inventory::getAvailableQuantity).sum(),
                "movements", runtime.movements.findAll().stream()
                        .filter(movement -> inventoryIds.contains(movement.getInventoryId()))
                        .sorted(Comparator.comparing(movement -> movement.getId().toString()))
                        .toList(),
                "orders", sellerOrders,
                "returns", sellerReturns,
                "salesByProduct", sales
        );
    }

    private Map<String, Object> userView(User user) {
        return Map.of(
                "id", user.getId(),
                "name", user.getFullName(),
                "email", user.getEmail().getValue(),
                "documentId", user.getDocumentId().getValue(),
                "role", user.getRole(),
                "status", user.getStatus());
    }

    private void requireRole(User actor, UserRole role) {
        if (actor.getRole() != role) {
            throw new UnauthorizedUserOperationException(
                    "Esta operación requiere el rol " + role + ".");
        }
    }

    private void requireOneOf(User actor, UserRole first, UserRole second) {
        if (actor.getRole() != first && actor.getRole() != second) {
            throw new UnauthorizedUserOperationException(
                    "Esta operación requiere rol de vendedor u operador logístico.");
        }
    }

    private String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring("Bearer ".length()).trim();
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("El cuerpo de la solicitud supera 1 MB.");
        }
        if (bytes.length == 0) return json.createObjectNode();
        return json.readTree(bytes);
    }

    private void serveIndex(HttpExchange exchange) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/static/index.html")) {
            if (input == null) {
                sendError(exchange, 404, "No se encontró la interfaz web.");
                return;
            }
            byte[] page = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, page.length);
            exchange.getResponseBody().write(page);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object value)
            throws IOException {
        byte[] response = json.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    private void sendError(HttpExchange exchange, int status, String message)
            throws IOException {
        String safeMessage = message == null ? "Error" : message;
        sendJson(exchange, status, Map.of("error", safeMessage));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("El campo " + field + " es obligatorio.");
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? fallback : value.asText().trim();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.canConvertToInt()) {
            throw new IllegalArgumentException("El campo " + field + " debe ser entero.");
        }
        return value.asInt();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Identificador UUID inválido: " + value);
        }
    }

    private static List<String> stringList(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        value.forEach(item -> {
            if (!item.isNull()) result.add(item.asText());
        });
        return List.copyOf(result);
    }

    private enum NoContent { INSTANCE }
}
