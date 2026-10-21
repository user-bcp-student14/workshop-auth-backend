package com.bcp.workshop.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.eclipse.microprofile.config.ConfigProvider;

final class DatabaseFixture {
    static final String STUDENT = "test-student";
    static final String TRAINER = "test-trainer";
    static final String PASSWORD = "Test-only-password!";
    static final String DISPLAY_NAME = "<Test & Student>";

    private DatabaseFixture() {}

    static Connection connection() throws SQLException {
        String url = ConfigProvider.getConfig().getValue("auth.database.url", String.class);
        if (url == null || !url.contains("target")) {
            throw new IllegalStateException("Tests require an isolated AUTH_DB_URL under target");
        }
        return DriverManager.getConnection(url);
    }

    static void reset() throws SQLException {
        try (Connection connection = connection();
             var delete = connection.createStatement()) {
            delete.executeUpdate("DELETE FROM legacy_users");
            insert(connection, STUDENT, PASSWORD, DISPLAY_NAME, "STUDENT");
            insert(connection, TRAINER, PASSWORD, "Test Trainer", "TRAINER");
        }
    }

    private static void insert(Connection connection, String username, String password,
                               String displayName, String role) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, new PasswordHasher(4).hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    public static class DirectoryFactory implements org.junit.jupiter.api.io.TempDirFactory {
        @Override
        public java.nio.file.Path createTempDirectory(
                org.junit.jupiter.api.extension.AnnotatedElementContext context,
                org.junit.jupiter.api.extension.ExtensionContext extension) throws java.io.IOException {
            return java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"), "auth-unit-");
        }
    }
}
