package com.example;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration resource providing config dump functionality.
 */
@Path("/config")
@RequestScoped
public class ConfigResource {

    @Inject
    private Config config;

    /**
     * Returns a flattened configuration dump as key=value pairs.
     * 
     * @return configuration dump in text format
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getConfig() {
        // Convert config to map and format as key=value lines
        java.util.List<String> keys = new java.util.ArrayList<>();
        config.getPropertyNames().forEach(keys::add);
        
        String body = keys.stream()
                .sorted()
                .map(key -> key + "=" + config.getOptionalValue(key, String.class).orElse(""))
                .collect(Collectors.joining("\n"));
        
        if (body.isEmpty()) {
            body = "(no application keys found)";
        }
        
        return body;
    }
}