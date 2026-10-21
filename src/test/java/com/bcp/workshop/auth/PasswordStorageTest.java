package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PasswordStorageTest {
    @Test
    void storesHashesInsteadOfPlaintextColumn() throws Exception {
        var columns = new HashSet<String>();
        try (var connection = DatabaseFixture.connection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        assertTrue(columns.contains("password_hash"), "The active schema must contain password_hash");
        assertFalse(columns.contains("password"), "The plaintext password column must be removed");
    }
}
