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
                            + "\",\"quantity\":2}");
            assertEquals(200, validAdd.statusCode(), validAdd.body());

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
