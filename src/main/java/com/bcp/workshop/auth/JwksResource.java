package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class JwksResource {
    @Inject
    TokenService tokens;

    @GET
    @Path("/.well-known/jwks.json")
    public Response publicKeys() {
        return Response.ok(tokens.publicJwks())
                .header("Cache-Control", "public, max-age=300")
                .build();
    }
}
