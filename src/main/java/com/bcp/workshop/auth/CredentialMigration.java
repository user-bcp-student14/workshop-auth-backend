package com.bcp.workshop.auth;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

final class CredentialMigration {
    private static final int HASHED_SCHEMA_VERSION = 1;
    private static final String CREATE_HASHED_TABLE = """
            CREATE TABLE legacy_users_hashed (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE,
                password_hash TEXT NOT NULL,
                display_name TEXT NOT NULL,
                role TEXT NOT NULL)
            """;
    private static final String INSERT_MIGRATED = """
            INSERT INTO legacy_users_hashed(id,username,password_hash,display_name,role)
            VALUES (?,?,?,?,?)
            """;
    private final PasswordHasher hasher;

    CredentialMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    void migrate(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            migrateSchema(connection);
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void migrateSchema(Connection connection) throws SQLException {
        int version = schemaVersion(connection);
        Set<String> columns = columns(connection);
        if (version == HASHED_SCHEMA_VERSION && columns.contains("password_hash") && !columns.contains("password")) {
            return;
        }
        if (version != 0) {
            throw new SQLException("Unsupported credential schema version");
        }
        if (!columns.isEmpty() && !columns.contains("password")) {
            throw new SQLException("Unsupported legacy credential schema");
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_HASHED_TABLE);
        }
        if (!columns.isEmpty()) {
            copyCredentials(connection);
        }
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS legacy_users");
            statement.executeUpdate("ALTER TABLE legacy_users_hashed RENAME TO legacy_users");
            statement.executeUpdate("PRAGMA user_version = " + HASHED_SCHEMA_VERSION);
        }
    }

    private void copyCredentials(Connection connection) throws SQLException {
        try (var select = connection.createStatement();
             var rows = select.executeQuery("SELECT id,username,password,display_name,role FROM legacy_users");
             var insert = connection.prepareStatement(INSERT_MIGRATED)) {
            while (rows.next()) {
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, rows.getString("username"));
                insert.setString(3, hasher.hash(rows.getString("password")));
                insert.setString(4, rows.getString("display_name"));
                insert.setString(5, rows.getString("role"));
                insert.executeUpdate();
            }
        }
    }

    private int schemaVersion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA user_version")) {
            return result.getInt(1);
        }
    }

    private Set<String> columns(Connection connection) throws SQLException {
        Set<String> names = new HashSet<>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                names.add(result.getString("name"));
            }
        }
        return names;
    }
}
