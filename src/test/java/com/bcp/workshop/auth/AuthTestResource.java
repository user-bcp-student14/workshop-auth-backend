package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.security.KeyPairGenerator;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

public class AuthTestResource implements QuarkusTestResourceLifecycleManager {
    private Path directory;
    private Driver driver;

    @Override
    public Map<String, String> start() {
        try {
            driver = new org.sqlite.JDBC();
            DriverManager.registerDriver(driver);
            directory = Files.createTempDirectory(Path.of("target"), "auth-test-").toAbsolutePath();
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            Path key = directory.resolve("test-key.pem");
            Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----");
            return Map.of("auth.database.url", "jdbc:sqlite:" + directory.resolve("test.db"),
                    "auth.password.cost", "4",
                    "auth.bootstrap.username", "",
                    "auth.bootstrap.password", "",
                    "auth.token.issuer", "test-issuer",
                    "auth.token.audience", "test-audience",
                    "auth.token.private-key-path", key.toString(),
                    "auth.cors.origins", CorsTest.ALLOWED_ORIGIN);
        } catch (java.io.IOException | GeneralSecurityException | SQLException error) {
            throw new IllegalStateException("Unable to create isolated test storage", error);
        }
    }

    @Override
    public void stop() {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
            Files.delete(directory);
            DriverManager.deregisterDriver(driver);
        } catch (java.io.IOException | SQLException error) {
            throw new IllegalStateException("Unable to remove isolated test storage", error);
        }
    }
}
