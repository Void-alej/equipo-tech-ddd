package com.nexusmarket.infrastructure.http;

/** Punto de entrada para ejecutar la aplicación web NexusMarket. */
public final class NexusMarketServer {

    private NexusMarketServer() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        NexusMarketRuntime runtime = new NexusMarketRuntime();
        runtime.initializeDemoAccounts();

        NexusMarketHttpServer server = new NexusMarketHttpServer(runtime, port);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> server.stop(2), "nexusmarket-shutdown"));

        server.start();
        System.out.println("NexusMarket disponible en http://localhost:" + port);
        System.out.println("Cuenta inicial: admin@nexusmarket.local");
        System.out.println("Contraseña inicial: Admin123! (cámbiala con NEXUSMARKET_ADMIN_PASSWORD)");
        System.out.println("Datos persistentes: "
                + System.getProperty("nexusmarket.data.dir"));
    }
}
