package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryLifecycleTest {
    @TempDir(factory = DatabaseFixture.DirectoryFactory.class)
    Path directory;
    private final org.sqlite.JDBC driver = new org.sqlite.JDBC();

    @BeforeEach
    void registerDriverForUnitTestClassLoader() throws SQLException {
        DriverManager.registerDriver(driver);
    }

    @AfterEach
    void unregisterDriver() throws SQLException {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void provisionsOnlyExplicitAccountsAndDoesNotResetExistingCredentials() {
        String url = "jdbc:sqlite:" + directory.resolve("provision.db");
        PasswordHasher hasher = new PasswordHasher(4);
        LegacyDatabase repository = new LegacyDatabase(url, hasher, Optional.of("bootstrap-test"),
                Optional.of(DatabaseFixture.PASSWORD));
        repository.initialize();
        repository.initialize();
        StoredCredentials credentials = repository.findByUsername("bootstrap-test");
        assertTrue(hasher.matches(DatabaseFixture.PASSWORD, credentials.passwordHash()));
        assertEquals("STUDENT", credentials.user().role());
        LegacyDatabase restarted = new LegacyDatabase(url, hasher, Optional.of("bootstrap-test"), Optional.of("different"));
        restarted.initialize();
        assertTrue(hasher.matches(DatabaseFixture.PASSWORD, restarted.findByUsername("bootstrap-test").passwordHash()));
    }

    @Test
    void failsInitializationAndLookupExplicitly() {
        PasswordHasher hasher = new PasswordHasher(4);
        LegacyDatabase unavailable = new LegacyDatabase("jdbc:unavailable:test", hasher, Optional.empty(), Optional.empty());
        assertThrows(IllegalStateException.class, unavailable::initialize);
        CredentialStorageException failure = assertThrows(CredentialStorageException.class,
                () -> unavailable.findByUsername("test"));
        assertInstanceOf(SQLException.class, failure.getCause());
        String url = "jdbc:sqlite:" + directory.resolve("invalid-bootstrap.db");
        LegacyDatabase incomplete = new LegacyDatabase(url, hasher, Optional.of("test"), Optional.empty());
        assertThrows(IllegalStateException.class, incomplete::initialize);
        LegacyDatabase blank = new LegacyDatabase(url, hasher, Optional.of(" "), Optional.of(" "));
        assertThrows(IllegalStateException.class, blank::initialize);
    }
}
