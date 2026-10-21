package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private final LegacyDatabase repository;
    private final PasswordHasher hasher;
    private final TokenService tokens;

    @Inject
    public AuthService(LegacyDatabase repository, PasswordHasher hasher, TokenService tokens) {
        this.repository = repository;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    public LoginResponse login(String username, String password, String client) {
        UserRecord user = authenticate(username, password);
        if (user == null) {
            return null;
        }
        return LoginResponse.from(user, tokens.create(user, client), TokenService.LIFETIME_SECONDS);
    }

    public UserRecord authenticate(String username, String password) {
        StoredCredentials credentials = repository.findByUsername(username);
        if (credentials == null || !hasher.matches(password, credentials.passwordHash())) {
            return null;
        }
        return credentials.user();
    }
}
