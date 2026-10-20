package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class LegacyDatabaseTest {
    @Inject
    AuthService service;


    @BeforeEach
    void isolateAccounts() throws Exception {
        DatabaseFixture.reset();
    }

    @Test
    void findsOnlyExactCredentials() {
        UserRecord user = service.authenticate(DatabaseFixture.STUDENT, DatabaseFixture.PASSWORD);
        assertEquals(DatabaseFixture.STUDENT, user.username());
        assertEquals(DatabaseFixture.DISPLAY_NAME, user.displayName());
        assertEquals("STUDENT", user.role());
        assertNull(service.authenticate(DatabaseFixture.STUDENT, "incorrect"));
        assertNull(service.authenticate("missing", DatabaseFixture.PASSWORD));
        assertNull(service.authenticate(" " + DatabaseFixture.STUDENT, DatabaseFixture.PASSWORD));
    }

    @Test
    void rejectsSqlSyntaxAsCredentials() {
        assertNull(service.authenticate(DatabaseFixture.STUDENT + "' --", "incorrect"));
        assertNull(service.authenticate("missing", "' OR '1'='1"));
    }

}
