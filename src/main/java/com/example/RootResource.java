package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/")
public class RootResource {
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public String root() {
    return "OK\n";
  }
}
