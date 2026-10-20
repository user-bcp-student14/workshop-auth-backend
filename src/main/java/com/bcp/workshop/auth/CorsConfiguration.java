package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;

@Startup
@ApplicationScoped
public class CorsConfiguration {
    private static final int MAX_PORT = 65535;
    private static final Logger LOG = Logger.getLogger(CorsConfiguration.class);

    @Inject
    public CorsConfiguration(@ConfigProperty(name = "auth.cors.origins") String origins) {
        if (origins == null || origins.isBlank()) {
            throw invalidOrigins();
        }
        for (String origin : origins.split(",", -1)) {
            validateOrigin(origin.trim());
        }
    }

    private void validateOrigin(String value) {
        try {
            URI origin = new URI(value);
            if (!("https".equals(origin.getScheme()) || "http".equals(origin.getScheme()))
                    || origin.getHost() == null || origin.getUserInfo() != null
                    || !origin.getRawPath().isEmpty() || origin.getQuery() != null || origin.getFragment() != null
                    || origin.getPort() == 0 || origin.getPort() > MAX_PORT) {
                throw invalidOrigins();
            }
        } catch (URISyntaxException error) {
            throw invalidOrigins();
        }
    }

    private IllegalArgumentException invalidOrigins() {
        LOG.error("AUTH_CORS_ORIGINS must contain explicit HTTP(S) origins without paths or wildcards");
        return new IllegalArgumentException("Invalid CORS origins configuration");
    }
}
