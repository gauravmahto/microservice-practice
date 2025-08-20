package com.example;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;

/**
 * Greeting resource providing the main endpoint functionality.
 */
@Path("/")
@RequestScoped
public class GreetingResource {

    @Inject
    @ConfigProperty(name = "app.greeting", defaultValue = "Hello")
    private String greeting;

    /**
     * Returns a greeting message with current timestamp.
     * 
     * @return greeting message with timestamp
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getGreeting() {
        return greeting + " @ " + Instant.now();
    }
}