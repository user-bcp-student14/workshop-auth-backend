package com.bcp.workshop.auth;

import java.sql.SQLException;

public class CredentialStorageException extends RuntimeException {
    public CredentialStorageException(SQLException cause) {
        super("Credential lookup failed", cause);
    }
}
