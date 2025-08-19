package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Path("/greet")
public class GreetingResource {

  @Inject
  @ConfigProperty(name = "app.greeting", defaultValue = "Hello from MP!")
  String greeting;

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String greet() {
    return greeting + "\n";
  }
}
