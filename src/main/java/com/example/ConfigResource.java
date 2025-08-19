package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;

import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
@Path("/config")
public class ConfigResource {

  @Inject
  Config config;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, String> dump() {
    Map<String, String> out = new TreeMap<>();
    for (String name : config.getPropertyNames()) {
      if (name.startsWith("server.") || name.startsWith("app.")) {
        out.put(name, config.getValue(name, String.class));
      }
    }
    return out;
  }
}
