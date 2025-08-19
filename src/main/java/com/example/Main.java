package com.example;

import io.helidon.microprofile.server.Server;

/**
 * Entry point for Helidon MP application.
 */
public class Main {
    public static void main(String[] args) {
        Server.create().start();
    }
}
