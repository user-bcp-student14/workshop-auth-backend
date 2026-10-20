package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher(4);

    @Test
    void saltsEachCredentialAndChecksFullPassword() {
        String hash = hasher.hash(DatabaseFixture.PASSWORD);
        assertNotEquals(hash, hasher.hash(DatabaseFixture.PASSWORD));
        assertFalse(hash.contains(DatabaseFixture.PASSWORD));
        assertTrue(hasher.matches(DatabaseFixture.PASSWORD, hash));
        assertFalse(hasher.matches("incorrect", hash));
        String longPassword = "x".repeat(100);
        assertFalse(hasher.matches(longPassword + "different", hasher.hash(longPassword)));
        assertFalse(hasher.matches("a\u0000c", hasher.hash("a\u0000b")));
        assertTrue(hasher.matches("clave-\u00f1", hasher.hash("clave-\u00f1")));
    }

    @Test
    void rejectsInvalidCostAndUnsupportedHashes() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(3));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(17));
        assertThrows(IllegalArgumentException.class, () -> hasher.matches("test", "plaintext"));
    }
}
