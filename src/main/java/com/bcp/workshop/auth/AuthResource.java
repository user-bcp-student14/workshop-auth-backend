package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String MESSAGE_FIELD = "message";
    private static final String REQUEST_REQUIRED = "Request is null";
    private static final String USERNAME_REQUIRED = "Username is required";
    private static final String PASSWORD_REQUIRED = "Password is required";
    private static final String INVALID_CREDENTIALS = "Invalid user %s or password";

    @Inject
    AuthService service;
    @Inject
    AuthExceptionMapper errors;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            String validationMessage = validate(request);
            if (validationMessage != null) {
                return reject(Response.Status.BAD_REQUEST, validationMessage);
            }
            return responseFor(service.login(request.username, request.password), request.username);
        } catch (Exception error) {
            return errors.toResponse(error);
        }
    }

    private Response responseFor(LoginResponse login, String username) {
        if (login == null) {
            return reject(Response.Status.UNAUTHORIZED, INVALID_CREDENTIALS.formatted(username));
        }
        return Response.ok(login).build();
    }

    private Response reject(Response.Status status, String message) {
        return Response.status(status).entity(Map.of(MESSAGE_FIELD, message)).build();
    }

    private String validate(LoginRequest request) {
        if (request == null) {
            return REQUEST_REQUIRED;
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return USERNAME_REQUIRED;
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return PASSWORD_REQUIRED;
        }
        return null;
    }
}
