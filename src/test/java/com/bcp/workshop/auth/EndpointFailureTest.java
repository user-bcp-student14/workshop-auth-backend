package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(AuthTestResource.class)
@TestProfile(EndpointFailureTest.FailureProfile.class)
class EndpointFailureTest {
    @Test
    void hidesInternalDetailsForFailuresWithAndWithoutMessages() {
        for (String username : new String[]{"synthetic-failure", "null-message"}) {
            given().contentType("application/json")
                    .body(Map.of("username", username, "password", "test-only", "client", "test-client"))
                    .post("/auth/login").then().statusCode(500)
                    .body("", equalTo(Map.of("message", "Internal server error")));
        }
    }

    @Test
    void reportsStorageFailureAsGenericServerErrorInsteadOfInvalidCredentials() {
        given().contentType("application/json")
            .body(Map.of("username", "storage-failure", "password", "test-only", "client", "test-client"))
                .post("/auth/login").then().statusCode(500)
                .body("", equalTo(Map.of("message", "Internal server error")));
    }

    @Test
    void reportsGenericInternalErrorOverHttp() {
        given().contentType("application/json")
            .body(Map.of("username", "synthetic-failure", "password", "test-only", "client", "test-client"))
                .post("/auth/login").then().statusCode(500)
                .body("", equalTo(Map.of("message", "Internal server error")));
    }

    @Test
    void doesNotLogSensitiveSentinelsDuringFailedLogin() {
        var records = new ArrayList<LogRecord>();
        Logger logger = Logger.getLogger(AuthExceptionMapper.class.getName());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(capture);
        String password = "password-log-sentinel";
        String token = "token-log-sentinel";
        String secret = "private-key-log-sentinel";
        try {
            given().contentType("application/json")
                    .body(Map.of("username", "synthetic-failure", "password", password, "client", "test-client"))
                    .post("/auth/login").then().statusCode(500)
                    .body("", equalTo(Map.of("message", "Internal server error")));
                assertEquals(1, records.size());
            String logged = String.join("\n",
                    records.stream().map(record -> new SimpleFormatter().format(record)).toList());
            assertFalse(logged.contains(password));
            assertFalse(logged.contains(token));
            assertFalse(logged.contains(secret));
        } finally {
            logger.removeHandler(capture);
        }
    }

    @Test
    void usesSameGenericErrorWhenExceptionMessageIsNull() {
        given().contentType("application/json")
            .body(Map.of("username", "null-message", "password", "test-only", "client", "test-client"))
                .post("/auth/login").then().statusCode(500)
                .body("", equalTo(Map.of("message", "Internal server error")));
    }

    @Test
    void reportsUnavailableHealthWhenAnIsolatedCheckIsDown() {
        given().accept("application/json").get("/q/health").then()
                .statusCode(503).contentType("application/json").body("status", is("DOWN"))
                .body("checks.size()", is(1))
                .body("checks[0].name", is("synthetic-down"))
                .body("checks[0].status", is("DOWN"));
    }

    public static class FailureProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingAuthService.class, DownHealthCheck.class);
        }
    }

    @Alternative
    @ApplicationScoped
    public static class FailingAuthService extends AuthService {
        public FailingAuthService() {
            super(null, null, null);
        }

        @Override
        public UserRecord authenticate(String username, String password) {
            if ("storage-failure".equals(username)) {
                LegacyDatabase unavailable = new LegacyDatabase("jdbc:unavailable:test", null,
                        Optional.empty(), Optional.empty());
                unavailable.findByUsername(username);
                return null;
            }
            if ("null-message".equals(username)) {
                throw new IllegalStateException();
            }
            throw new IllegalStateException("password=" + password + " token=token-log-sentinel secret=private-key-log-sentinel");
        }
    }

    @Alternative
    @ApplicationScoped
    @Liveness
    public static class DownHealthCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("synthetic-down").down().build();
        }
    }
}
