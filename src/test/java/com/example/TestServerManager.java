package com.example;

import io.helidon.microprofile.server.Server;

/**
 * Test helper to start and manage Helidon MP server for integration tests.
 */
public class TestServerManager {
    
    private static Server server;
    
    /**
     * Start the MP server on an ephemeral port.
     * 
     * @return the port the server is listening on
     */
    public static int startServer() {
        if (server != null) {
            return server.port();
        }
        
        // Set ephemeral port for testing
        System.setProperty("server.port", "0");
        
        // Start MP server directly
        server = Server.create().start();
        return server.port();
    }
    
    /**
     * Stop the MP server.
     */
    public static void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
    
    /**
     * Get the server port.
     * 
     * @return server port or -1 if not started
     */
    public static int getPort() {
        return server != null ? server.port() : -1;
    }
}