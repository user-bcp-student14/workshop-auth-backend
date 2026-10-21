package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private static final UserRecord STORED_USER = new UserRecord(42, "stored-user", "<Stored>", "TRAINER");
    private static final String INPUT_USERNAME = "requested-user";
    private static final String INPUT_PASSWORD = "isolated-test-password";
    private static final String CLIENT = "test-client";
    private static final String STORED_HASH = "isolated-test-hash";
    private static final String ISSUED_TOKEN = "isolated-test-token";
    private static PrivateKey signingKey;

    @BeforeAll
    static void createIsolatedSigningKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKey = generator.generateKeyPair().getPrivate();
    }

    @Test
    void emitsExactlyOnceWithStoredIdentityAfterSuccessfulVerification() {
        Scenario scenario = new Scenario();
        try (var response = scenario.resource.login(validRequest())) {
            assertEquals(200, response.getStatus());
            assertEquals(new LoginResponse(ISSUED_TOKEN, TokenService.LIFETIME_SECONDS,
                    new LoginResponse.UserResponse(STORED_USER.username(), "&lt;Stored&gt;", STORED_USER.role()),
                    "authenticated-with-legacy-flow"), response.getEntity());
        }
        assertEquals(1, scenario.lookups);
        assertEquals(1, scenario.verifications);
        assertEquals(1, scenario.emissions);
    }

    @Test
    void doesNotVerifyOrEmitForAnAbsentAccount() {
        Scenario scenario = new Scenario();
        scenario.credentials = null;
        assertRejected(scenario);
        assertEquals(1, scenario.lookups);
        assertEquals(0, scenario.verifications);
        assertEquals(0, scenario.emissions);
    }

    @Test
    void doesNotEmitWhenPasswordVerificationFails() {
        Scenario scenario = new Scenario();
        scenario.passwordMatches = false;
        assertRejected(scenario);
        assertEquals(1, scenario.verifications);
        assertEquals(0, scenario.emissions);
    }

    @Test
    void doesNotReachBusinessLogicBeforeValidationSucceeds() {
        Scenario scenario = new Scenario();
        try (var response = scenario.resource.login(new LoginRequest())) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Username is required"), response.getEntity());
        }
        assertEquals(0, scenario.lookups);
        assertEquals(0, scenario.verifications);
        assertEquals(0, scenario.emissions);
    }

    @Test
    void preservesGenericFailureAndDoesNotEmitWhenStorageFails() {
        Scenario scenario = new Scenario();
        scenario.lookupFailure = new CredentialStorageException(new SQLException("isolated-storage-error"));
        assertServerError(scenario);
        assertEquals(0, scenario.verifications);
        assertEquals(0, scenario.emissions);
    }

    @Test
    void preservesGenericFailureWhenTokenIssuanceFails() {
        Scenario scenario = new Scenario();
        scenario.signingFailure = new IllegalStateException("isolated-signing-error");
        assertServerError(scenario);
        assertEquals(1, scenario.verifications);
        assertEquals(1, scenario.emissions);
    }

    @Test
    void assignsTokenOrchestrationToServiceNotHttpAdapter() {
        assertFalse(Arrays.stream(AuthResource.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == TokenService.class));
        assertTrue(Arrays.stream(AuthService.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == TokenService.class));
    }

    @Test
    void serviceReturnsTheCompletePublicLoginWithoutAnHttpResource() {
        Scenario scenario = new Scenario();
        LoginResponse login = scenario.service.login(INPUT_USERNAME, INPUT_PASSWORD, CLIENT);
        assertEquals(ISSUED_TOKEN, login.token());
        assertEquals(TokenService.LIFETIME_SECONDS, login.expiresIn());
        assertEquals(STORED_USER.username(), login.user().username());
        assertEquals(1, scenario.emissions);
    }

    @Test
    void serviceRejectsIncorrectCredentialsWithoutIssuingAToken() {
        Scenario scenario = new Scenario();
        scenario.passwordMatches = false;
        assertNull(scenario.service.login(INPUT_USERNAME, INPUT_PASSWORD, CLIENT));
        assertEquals(0, scenario.emissions);
    }

    @Test
    void servicePropagatesInfrastructureFailuresForHttpTranslation() {
        Scenario scenario = new Scenario();
        scenario.lookupFailure = new CredentialStorageException(new SQLException("isolated-error"));
        assertSame(scenario.lookupFailure, assertThrows(CredentialStorageException.class,
                () -> scenario.service.login(INPUT_USERNAME, INPUT_PASSWORD, CLIENT)));
        assertEquals(0, scenario.emissions);
    }

    private static void assertRejected(Scenario scenario) {
        try (var response = scenario.resource.login(validRequest())) {
            assertEquals(401, response.getStatus());
            assertEquals(Map.of("message", "Invalid user " + INPUT_USERNAME + " or password"), response.getEntity());
        }
    }

    private static void assertServerError(Scenario scenario) {
        try (var response = scenario.resource.login(validRequest())) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", "Internal server error"), response.getEntity());
        }
    }

    private static LoginRequest validRequest() {
        LoginRequest request = new LoginRequest();
        request.username = INPUT_USERNAME;
        request.password = INPUT_PASSWORD;
        request.client = CLIENT;
        return request;
    }

    private static class Scenario {
        StoredCredentials credentials = new StoredCredentials(STORED_USER, STORED_HASH);
        boolean passwordMatches = true;
        RuntimeException lookupFailure;
        RuntimeException signingFailure;
        int lookups;
        int verifications;
        int emissions;
        final AuthService service;
        final AuthResource resource = new AuthResource();

        Scenario() {
            LegacyDatabase repository = new LegacyDatabase("unused", null, Optional.empty(), Optional.empty()) {
                @Override
                public StoredCredentials findByUsername(String username) {
                    lookups++;
                    assertEquals(INPUT_USERNAME, username);
                    if (lookupFailure != null) {
                        throw lookupFailure;
                    }
                    return credentials;
                }
            };
            PasswordHasher hasher = new PasswordHasher(4) {
                @Override
                public boolean matches(String password, String storedHash) {
                    verifications++;
                    assertEquals(INPUT_PASSWORD, password);
                    assertEquals(STORED_HASH, storedHash);
                    return passwordMatches;
                }
            };
            TokenService tokens = new TokenService(signingKey, Clock.systemUTC(), "test-issuer", "test-audience") {
                @Override
                public String create(UserRecord user, String client) {
                    emissions++;
                    assertSame(STORED_USER, user);
                    assertEquals(CLIENT, client);
                    if (signingFailure != null) {
                        throw signingFailure;
                    }
                    return ISSUED_TOKEN;
                }
            };
            service = new AuthService(repository, hasher, tokens);
            resource.service = service;
            resource.errors = new AuthExceptionMapper();
        }
    }
}
