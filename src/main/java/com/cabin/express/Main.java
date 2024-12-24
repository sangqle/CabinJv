package com.cabin.express;

public class Main {
    public static void main(String[] args) {
        boolean enableDebug = args.length > 0 && args[0].equalsIgnoreCase("--debug");
        CabinJLogger.setDebug(enableDebug);

        CabinJLogger.info("Starting CabinJ Framework...");
        try {
            CabinJServer server = new CabinJServer();
            server.listen(8080);
        } catch (Exception e) {
            CabinJLogger.error("Failed to start the server", e);
        }
    }
}