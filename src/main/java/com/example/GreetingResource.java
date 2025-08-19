package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * JAX-RS resource providing greeting and config endpoints.
 */
@Path("/")
@ApplicationScoped
public class GreetingResource {
    private final Config config = ConfigProvider.getConfig();

    @GET
    @Path("")
    @Produces(MediaType.TEXT_PLAIN)
    public String greet() {
        String greeting = config.getOptionalValue("app.greeting", String.class).orElse("Hello");
        return greeting + " @ " + Instant.now();
    }

    @GET
    @Path("config")
    @Produces(MediaType.TEXT_PLAIN)
    public String config() {
        return StreamSupport.stream(config.getPropertyNames().spliterator(), false)
                .sorted()
                .map(name -> name + "=" + config.getOptionalValue(name, String.class).orElse(""))
                .collect(Collectors.joining("\n"));
    }
}
