package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class CredentialMigrationTest {
    @TempDir(factory = DatabaseFixture.DirectoryFactory.class)
    Path directory;

    private final PasswordHasher hasher = new PasswordHasher(4);

    @Test
    void migratesLegacyAccountsAndIsIdempotentAcrossRestarts() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("migration.db");
        try (var connection = open(url)) {
            createLegacyAccount(connection);
            new CredentialMigration(hasher).migrate(connection);
            assertMigrated(connection);
        }
        try (var connection = open(url)) {
            String before = storedHash(connection);
            new CredentialMigration(hasher).migrate(connection);
            assertEquals(before, storedHash(connection));
            assertMigrated(connection);
        }
    }

    @Test
    void rollsBackOnHashFailureWithoutLosingLegacyAccount() throws Exception {
        try (var connection = open("jdbc:sqlite::memory:")) {
            createLegacyAccount(connection);
            PasswordHasher failingHasher = new PasswordHasher(4) {
                @Override
                public String hash(String password) {
                    throw new IllegalStateException("Synthetic migration failure");
                }
            };
            assertThrows(IllegalStateException.class,
                    () -> new CredentialMigration(failingHasher).migrate(connection));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT password FROM legacy_users")) {
                assertEquals(DatabaseFixture.PASSWORD, result.getString(1));
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT count(*) FROM sqlite_master WHERE name='legacy_users_hashed'")) {
                assertEquals(0, result.getInt(1));
            }
            new CredentialMigration(hasher).migrate(connection);
            assertMigrated(connection);
        }
    }

    @Test
    void initializesEmptyDatabaseAndRejectsUnknownSchemas() throws Exception {
        try (var connection = open("jdbc:sqlite::memory:")) {
            new CredentialMigration(hasher).migrate(connection);
            new CredentialMigration(hasher).migrate(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("PRAGMA user_version=2");
            }
            assertThrows(SQLException.class, () -> new CredentialMigration(hasher).migrate(connection));
        }
        try (var connection = open("jdbc:sqlite::memory:")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE legacy_users(unexpected TEXT)");
            }
            assertThrows(SQLException.class, () -> new CredentialMigration(hasher).migrate(connection));
        }
    }

    private Connection open(String url) throws SQLException {
        var source = new org.sqlite.SQLiteDataSource();
        source.setUrl(url);
        return source.getConnection();
    }

    private void createLegacyAccount(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }
        try (var statement = connection.prepareStatement(
                "INSERT INTO legacy_users VALUES (1,?,?,?,?)")) {
            statement.setString(1, DatabaseFixture.STUDENT);
            statement.setString(2, DatabaseFixture.PASSWORD);
            statement.setString(3, DatabaseFixture.DISPLAY_NAME);
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }

    private String storedHash(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT password_hash FROM legacy_users")) {
            return result.getString(1);
        }
    }

    private void assertMigrated(Connection connection) throws SQLException {
        assertTrue(hasher.matches(DatabaseFixture.PASSWORD, storedHash(connection)));
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT id,username,display_name,role FROM legacy_users")) {
            assertEquals(1, result.getInt("id"));
            assertEquals(DatabaseFixture.STUDENT, result.getString("username"));
            assertEquals(DatabaseFixture.DISPLAY_NAME, result.getString("display_name"));
            assertEquals("STUDENT", result.getString("role"));
        }
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                assertNotEquals("password", result.getString("name"));
            }
        }
    }
}
