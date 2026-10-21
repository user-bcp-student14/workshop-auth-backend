package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.Map;

@ApplicationScoped
public class AuthExceptionMapper {
    private static final Logger LOG = Logger.getLogger(AuthExceptionMapper.class);
    private static final String PUBLIC_ERROR = "Internal server error";

    public Response toResponse(Exception error) {
        // Exception messages can contain credentials; retain type and stack, not arbitrary messages.
        LOG.errorf("Authentication failed: type=%s, stack=%s",
                error.getClass().getName(), Arrays.toString(error.getStackTrace()));
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("message", PUBLIC_ERROR)).build();
    }
}
