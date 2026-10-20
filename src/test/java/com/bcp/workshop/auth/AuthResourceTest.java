package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@QuarkusTest
@QuarkusTestResource(AuthTestResource.class)
class AuthResourceTest {
    @BeforeEach
    void isolateAccounts() throws Exception {
        DatabaseFixture.reset();
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body(Map.of("username", DatabaseFixture.STUDENT, "password", DatabaseFixture.PASSWORD,
                        "client", "test-client", "unknownField", true))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is(DatabaseFixture.STUDENT))
                .body("user.displayName", is("&lt;Test &amp; Student&gt;"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("size()", is(4))
                .body("user.size()", is(3))
                .body("token", notNullValue());
    }

    @Test
    void logsInTrainerWithoutClient() {
        given().contentType("application/json")
                .body(Map.of("username", DatabaseFixture.TRAINER, "password", DatabaseFixture.PASSWORD))
                .post("/auth/login").then().statusCode(200).body("user.role", is("TRAINER"));
    }

    @Test
    void acceptsNullOrEmptyClientAndVerifiesCompleteLongPassword() throws Exception {
        for (String client : new String[]{"null", "\"\""}) {
            given().contentType("application/json")
                    .body("{\"username\":\"" + DatabaseFixture.STUDENT + "\",\"password\":\""
                            + DatabaseFixture.PASSWORD + "\",\"client\":" + client + "}")
                    .post("/auth/login").then().statusCode(200)
                    .body("user.username", is(DatabaseFixture.STUDENT));
        }
        String longPassword = "test-only-".repeat(12);
        try (var connection = DatabaseFixture.connection();
             var statement = connection.prepareStatement("UPDATE legacy_users SET password_hash=? WHERE username=?")) {
            statement.setString(1, new PasswordHasher(4).hash(longPassword));
            statement.setString(2, DatabaseFixture.STUDENT);
            statement.executeUpdate();
        }
        given().contentType("application/json")
                .body(Map.of("username", DatabaseFixture.STUDENT, "password", longPassword))
                .post("/auth/login").then().statusCode(200);
        assertRejected(DatabaseFixture.STUDENT, longPassword + "different-suffix");
    }

    @Test
    void preservesValidationMessagesAndPrecedence() {
        assertInvalid(Map.of(), "Username is required");
        assertInvalid(Map.of("username", " "), "Username is required");
        assertInvalid(Map.of("username", DatabaseFixture.STUDENT), "Password is required");
        assertInvalid(Map.of("username", DatabaseFixture.STUDENT, "password", " "), "Password is required");
        assertInvalid(Map.of("username", "", "password", ""), "Username is required");
    }

    @Test
    void rejectsIncorrectAndUntrimmedCredentials() {
        assertRejected(DatabaseFixture.STUDENT, "incorrect");
        assertRejected("missing-user", DatabaseFixture.PASSWORD);
        assertRejected(" " + DatabaseFixture.STUDENT, DatabaseFixture.PASSWORD);
        assertRejected(DatabaseFixture.STUDENT, DatabaseFixture.PASSWORD + " ");
    }

    @Test
    void preservesNullRequestAndExceptionResponse() {
        AuthResource resource = new AuthResource();
        resource.errors = new AuthExceptionMapper();
        try (var response = resource.login(null)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Request is null"), response.getEntity());
        }
        resource.service = new AuthService(null, null, null) {
            @Override
            public UserRecord authenticate(String username, String password) {
                throw new IllegalStateException("Synthetic failure");
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = DatabaseFixture.STUDENT;
        request.password = DatabaseFixture.PASSWORD;
        try (var response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", "Internal server error"), response.getEntity());
        }
    }

    @Test
    void logsEachFailureOnceWithoutCredentialMaterialOrExceptionMessages() {
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
        String sensitiveMessage = "isolated-password hash token jdbc:private-location";
        try (var response = new AuthExceptionMapper().toResponse(
                new IllegalStateException(sensitiveMessage, new RuntimeException(sensitiveMessage)))) {
            assertEquals(Map.of("message", "Internal server error"), response.getEntity());
            assertEquals(1, records.size());
            String logged = new SimpleFormatter().format(records.getFirst());
            assertTrue(logged.contains(IllegalStateException.class.getName()));
            assertTrue(logged.contains("AuthResourceTest"));
            assertFalse(logged.contains(sensitiveMessage));
            assertEquals(null, records.getFirst().getThrown());
        } finally {
            logger.removeHandler(capture);
        }
    }

    private void assertInvalid(Map<String, String> request, String message) {
        given().contentType("application/json").body(request).post("/auth/login")
                .then().statusCode(400).body("message", is(message)).body("size()", is(1));
    }

    private void assertRejected(String username, String password) {
        given().contentType("application/json").body(Map.of("username", username, "password", password))
                .post("/auth/login").then().statusCode(401)
                .body("message", is("Invalid user " + username + " or password"));
    }
}
