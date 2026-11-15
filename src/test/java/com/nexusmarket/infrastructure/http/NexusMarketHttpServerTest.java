package com.nexusmarket.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmarket.domain.enums.ProductType;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.models.Product;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NexusMarketHttpServerTest {

    @TempDir
    Path temporaryDirectory;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void shouldAuthenticateUsersEnforceRolesAndBlockCartQuantitiesAboveStock()
            throws Exception {
        String previousDirectory =
                System.getProperty("nexusmarket.data.dir");
        NexusMarketHttpServer server = null;

        try {
            NexusMarketRuntime runtime =
                    new NexusMarketRuntime(temporaryDirectory);
            runtime.initializeDemoAccounts();

            server = new NexusMarketHttpServer(runtime, 0);
            server.start();
            String baseUrl = "http://localhost:" + server.getPort();

            HttpResponse<String> loginAdmin = request(
                    baseUrl, "POST", "/api/auth/login", null,
                    """
                    {"email":"admin@nexusmarket.local","password":"Admin123!"}
                    """);
            assertEquals(200, loginAdmin.statusCode());
            String adminToken = json.readTree(loginAdmin.body())
                    .path("token").asText();
            assertFalse(adminToken.isBlank());

            HttpResponse<String> health =
                    request(baseUrl, "GET", "/api/health", null, null);
            assertEquals(200, health.statusCode());

            HttpResponse<String> buyerCreated = request(
                    baseUrl, "POST", "/api/buyers", adminToken,
                    """
                    {
                      "fullName":"Comprador API",
                      "email":"buyer.api@example.com",
                      "documentId":"CC908999",
                      "primaryAddress":"Calle 10, Medellín",
                      "additionalAddresses":[],
                      "password":"Buyer123!"
                    }
                    """);
            assertEquals(200, buyerCreated.statusCode(), buyerCreated.body());
            String buyerId = json.readTree(buyerCreated.body())
                    .path("user").path("id").asText();
            assertFalse(buyerId.isBlank());

            HttpResponse<String> buyerProfile = request(
                    baseUrl, "GET", "/api/buyers/" + buyerId + "/profile",
                    adminToken, null);
            assertEquals(200, buyerProfile.statusCode());
            assertEquals(buyerId,
                    json.readTree(buyerProfile.body()).path("buyerId").asText());

            User administrator = runtime.users.findAll().stream()
                    .filter(user -> user.getRole() == UserRole.ADMINISTRATOR)
                    .findFirst().orElseThrow();

            User seller = runtime.userService.registerUserByAdministrator(
                    administrator,
                    "Vendedor API",
                    "seller.api@example.com",
                    "NIT908999",
                    UserRole.SELLER);
            Product physical = runtime.productService.createProduct(
                    seller,
                    "Producto con stock limitado",
                    "Producto físico para probar el control de inventario",
                    ProductType.PHYSICAL,
                    new BigDecimal("50000"));
            runtime.productService.publishProduct(seller, physical.getId());

            Warehouse marketplace = runtime.warehouses.findAll().stream()
                    .filter(warehouse -> warehouse.getType() == WarehouseType.MARKETPLACE)
                    .findFirst().orElseThrow();
            runtime.inventoryService.addStock(
                    runtime.getLogisticsOperator(),
                    physical.getId(),
                    marketplace.getId(),
                    2,
                    "Stock inicial de prueba HTTP");

            Product digital = runtime.productService.createProduct(
                    seller,
                    "Manual digital",
                    "Producto digital para probar pedidos mixtos",
                    ProductType.DIGITAL,
                    new BigDecimal("15000"));
            runtime.productService.publishProduct(seller, digital.getId());
            Product secondPhysical = runtime.productService.createProduct(
                    seller,
                    "Segundo producto físico",
                    "Segundo producto físico para comprobar aislamiento",
                    ProductType.PHYSICAL,
                    new BigDecimal("60000"));
            runtime.productService.publishProduct(seller, secondPhysical.getId());
            runtime.inventoryService.addStock(
                    runtime.getLogisticsOperator(),
                    secondPhysical.getId(),
                    marketplace.getId(),
                    2,
                    "Stock inicial segundo producto");

            Product secondDigital = runtime.productService.createProduct(
                    seller,
                    "Segundo manual digital",
                    "Segundo artículo digital de prueba",
                    ProductType.DIGITAL,
                    new BigDecimal("20000"));
            runtime.productService.publishProduct(seller, secondDigital.getId());

            runtime.authenticationService.setPassword(seller, "Seller123!");

            HttpResponse<String> loginBuyer = request(
                    baseUrl, "POST", "/api/auth/login", null,
                    """
                    {"email":"buyer.api@example.com","password":"Buyer123!"}
                    """);
            assertEquals(200, loginBuyer.statusCode());
            String buyerToken = json.readTree(loginBuyer.body())
                    .path("token").asText();

            HttpResponse<String> cartCreated = request(
                    baseUrl, "POST", "/api/carts", buyerToken, "{}");
            assertEquals(200, cartCreated.statusCode());
            String cartId = json.readTree(cartCreated.body())
                    .path("id").asText();

            HttpResponse<String> overStock = request(
                    baseUrl, "POST", "/api/carts/" + cartId + "/items",
                    buyerToken,
                    "{\"productId\":\"" + physical.getId()
                            + "\",\"quantity\":3}");
            assertEquals(409, overStock.statusCode());
            assertTrue(overStock.body().contains("Inventario insuficiente"));

            HttpResponse<String> unchangedCart = request(
                    baseUrl, "GET", "/api/carts/" + cartId, buyerToken, null);
            assertEquals(200, unchangedCart.statusCode());
            assertEquals(0,
                    json.readTree(unchangedCart.body()).path("items").size());

            HttpResponse<String> validAdd = request(
                    baseUrl, "POST", "/api/carts/" + cartId + "/items",
                    buyerToken,
                    "{\"productId\":\"" + physical.getId()
                            + "\",\"quantity\":1}");
            assertEquals(200, validAdd.statusCode(), validAdd.body());

            HttpResponse<String> secondPhysicalAdd = request(
                    baseUrl, "POST", "/api/carts/" + cartId + "/items",
                    buyerToken,
                    "{\"productId\":\"" + secondPhysical.getId()
                            + "\",\"quantity\":1}");
            assertEquals(200, secondPhysicalAdd.statusCode(), secondPhysicalAdd.body());

            HttpResponse<String> digitalAdd = request(
                    baseUrl, "POST", "/api/carts/" + cartId + "/items",
                    buyerToken,
                    "{\"productId\":\"" + digital.getId()
                            + "\",\"quantity\":1}");
            assertEquals(200, digitalAdd.statusCode(), digitalAdd.body());

            HttpResponse<String> secondDigitalAdd = request(
                    baseUrl, "POST", "/api/carts/" + cartId + "/items",
                    buyerToken,
                    "{\"productId\":\"" + secondDigital.getId()
                            + "\",\"quantity\":1}");
            assertEquals(200, secondDigitalAdd.statusCode(), secondDigitalAdd.body());

            HttpResponse<String> orderCreated = request(
                    baseUrl, "POST", "/api/orders", buyerToken,
                    "{\"cartId\":\"" + cartId + "\"}");
            assertEquals(200, orderCreated.statusCode(), orderCreated.body());
            String orderId = json.readTree(orderCreated.body())
                    .path("id").asText();

            HttpResponse<String> orders = request(
                    baseUrl, "GET", "/api/orders", buyerToken, null);
            assertEquals(200, orders.statusCode());
            assertEquals(orderId,
                    json.readTree(orders.body()).get(0).path("id").asText());

            HttpResponse<String> paid = request(
                    baseUrl, "POST", "/api/orders/" + orderId + "/pay",
                    buyerToken, "{\"approved\":true}");
            assertEquals(200, paid.statusCode(), paid.body());

            HttpResponse<String> afterPayment = request(
                    baseUrl, "GET", "/api/orders/" + orderId, buyerToken, null);
            assertEquals(200, afterPayment.statusCode());
            assertTrue(json.readTree(afterPayment.body())
                    .path("digitallyDeliveredProductIds").toString()
                    .contains(digital.getId().toString()));

            HttpResponse<String> loginLogistics = request(
                    baseUrl, "POST", "/api/auth/login", null,
                    """
                    {"email":"logistica@nexusmarket.local","password":"Logistica123!"}
                    """);
            assertEquals(200, loginLogistics.statusCode());
            String logisticsToken = json.readTree(loginLogistics.body())
                    .path("token").asText();

            HttpResponse<String> shipmentsCreated = request(
                    baseUrl, "POST", "/api/orders/" + orderId + "/shipments",
                    logisticsToken, "{}");
            assertEquals(200, shipmentsCreated.statusCode(), shipmentsCreated.body());
            String shipmentId = json.readTree(shipmentsCreated.body())
                    .get(0).path("id").asText();
            assertFalse(shipmentId.isBlank());

            for (String transition : new String[] {
                    "pack", "dispatch", "transit", "deliver"
            }) {
                HttpResponse<String> shipmentTransition = request(
                        baseUrl, "POST", "/api/shipments/" + shipmentId
                                + "/" + transition,
                        logisticsToken,
                        "{\"description\":\"Prueba automatizada " + transition + "\"}");
                assertEquals(200, shipmentTransition.statusCode(),
                        transition + ": " + shipmentTransition.body());
            }

            HttpResponse<String> deliveredOrder = request(
                    baseUrl, "GET", "/api/orders/" + orderId, buyerToken, null);
            assertEquals(200, deliveredOrder.statusCode());
            assertEquals("DELIVERED",
                    json.readTree(deliveredOrder.body()).path("status").asText());

            HttpResponse<String> returnRequested = request(
                    baseUrl, "POST", "/api/orders/" + orderId + "/returns",
                    buyerToken,
                    "{\"productId\":\"" + physical.getId()
                            + "\",\"quantity\":1,\"reason\":\"Prueba de devolución\"}");
            assertEquals(200, returnRequested.statusCode(), returnRequested.body());
            String returnId = json.readTree(returnRequested.body())
                    .path("id").asText();

            HttpResponse<String> returnApproved = request(
                    baseUrl, "POST", "/api/returns/" + returnId + "/approve",
                    adminToken, "{}");
            assertEquals(200, returnApproved.statusCode(), returnApproved.body());

            HttpResponse<String> returnReceived = request(
                    baseUrl, "POST", "/api/returns/" + returnId + "/receive",
                    logisticsToken,
                    "{\"warehouseId\":\"" + marketplace.getId()
                            + "\",\"condition\":\"SELLABLE\"}");
            assertEquals(200, returnReceived.statusCode(), returnReceived.body());

            HttpResponse<String> returnReadByLogistics = request(
                    baseUrl, "GET", "/api/returns/" + returnId,
                    logisticsToken, null);
            assertEquals(200, returnReadByLogistics.statusCode());
            assertEquals("REFUND_PENDING",
                    json.readTree(returnReadByLogistics.body())
                            .path("status").asText());

            HttpResponse<String> refund = request(
                    baseUrl, "POST", "/api/returns/" + returnId + "/refund",
                    adminToken, "{\"refundReference\":\"REFUND-HTTP-001\"}");
            assertEquals(200, refund.statusCode(), refund.body());

            HttpResponse<String> loginSeller = request(
                    baseUrl, "POST", "/api/auth/login", null,
                    """
                    {"email":"seller.api@example.com","password":"Seller123!"}
                    """);
            assertEquals(200, loginSeller.statusCode());
            String sellerToken = json.readTree(loginSeller.body())
                    .path("token").asText();

            HttpResponse<String> sellerReport = request(
                    baseUrl, "GET", "/api/reports/seller", sellerToken, null);
            assertEquals(200, sellerReport.statusCode(), sellerReport.body());
            JsonNode report = json.readTree(sellerReport.body());
            JsonNode physicalSales = null;
            for (JsonNode item : report.path("salesByProduct")) {
                if (physical.getId().toString().equals(
                        item.path("productId").asText())) {
                    physicalSales = item;
                    break;
                }
            }
            assertNotNull(physicalSales);
            assertEquals(2, physicalSales.path("unitsSold").asInt());
            assertEquals(1, physicalSales.path("unitsRefunded").asInt());
            assertEquals(0, physicalSales.path("unitsInReturnProcess").asInt());
            assertEquals(0, new BigDecimal("100000")
                    .compareTo(physicalSales.path("grossSales").decimalValue()));
            assertEquals(0, new BigDecimal("50000")
                    .compareTo(physicalSales.path("refundedAmount").decimalValue()));
            assertEquals(0, new BigDecimal("50000")
                    .compareTo(physicalSales.path("netSalesAfterRefunds").decimalValue()));
            assertEquals(1, report.path("availableInventory").asInt());

            HttpResponse<String> buyerInventory = request(
                    baseUrl, "GET", "/api/inventory", buyerToken, null);
            assertEquals(403, buyerInventory.statusCode());

            HttpResponse<String> buyerForbiddenWrite = request(
                    baseUrl, "POST", "/api/inventory", buyerToken, "{}");
            assertEquals(403, buyerForbiddenWrite.statusCode());

            HttpResponse<String> buyerForbiddenUsers = request(
                    baseUrl, "GET", "/api/users", buyerToken, null);
            assertEquals(403, buyerForbiddenUsers.statusCode());
        } finally {
            if (server != null) {
                server.stop(0);
            }
            if (previousDirectory == null) {
                System.clearProperty("nexusmarket.data.dir");
            } else {
                System.setProperty("nexusmarket.data.dir", previousDirectory);
            }
        }
    }

    private HttpResponse<String> request(
            String baseUrl,
            String method,
            String path,
            String token,
            String body) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json");

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.GET();
        }

        return client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
