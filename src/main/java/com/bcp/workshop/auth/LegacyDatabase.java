package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

@Startup
@ApplicationScoped
public class LegacyDatabase {
    private static final Logger LOG = Logger.getLogger(LegacyDatabase.class);
    private static final String FIND_BY_USERNAME =
            "SELECT id,username,password_hash,display_name,role FROM legacy_users WHERE username=?";
    private final String databaseUrl;
    private final PasswordHasher hasher;
    private final Optional<String> initialUsername;
    private final Optional<String> initialPassword;

    @Inject
    public LegacyDatabase(@ConfigProperty(name = "auth.database.url") String databaseUrl,
                          PasswordHasher hasher,
                          @ConfigProperty(name = "auth.bootstrap.username") Optional<String> initialUsername,
                          @ConfigProperty(name = "auth.bootstrap.password") Optional<String> initialPassword) {
        this.databaseUrl = databaseUrl;
        this.hasher = hasher;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                new CredentialMigration(hasher).migrate(connection);
                provisionAccount(connection);
            }
        } catch (SQLException | IOException error) {
            LOG.error("Credential storage initialization failed", error);
            throw new IllegalStateException("Credential storage initialization failed", error);
        }
    }

    public StoredCredentials findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             var statement = connection.prepareStatement(FIND_BY_USERNAME)) {
            statement.setString(1, username);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                UserRecord user = new UserRecord(result.getLong("id"), result.getString("username"),
                        result.getString("display_name"), result.getString("role"));
                return new StoredCredentials(user, result.getString("password_hash"));
            }
        } catch (SQLException error) {
            throw new CredentialStorageException(error);
        }
    }

    private void provisionAccount(Connection connection) throws SQLException {
        if (initialUsername.isEmpty() && initialPassword.isEmpty()) {
            return;
        }
        if (initialUsername.isEmpty() || initialPassword.isEmpty()
                || initialUsername.get().isBlank() || initialPassword.get().isBlank()) {
            throw new IllegalStateException("Bootstrap username and password must both be configured");
        }
        try (var statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)
                """)) {
            statement.setString(1, initialUsername.get());
            statement.setString(2, hasher.hash(initialPassword.get()));
            statement.setString(3, initialUsername.get());
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }
}
