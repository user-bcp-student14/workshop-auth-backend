package com.bcp.workshop.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String SCHEME = "{bcrypt-sha256}";
    private static final int MIN_COST = 4;
    private static final int MAX_COST = 16;
    private final int cost;

    @Inject
    public PasswordHasher(@ConfigProperty(name = "auth.password.cost") int cost) {
        if (cost < MIN_COST || cost > MAX_COST) {
            throw new IllegalArgumentException("BCrypt cost must be between 4 and 16");
        }
        this.cost = cost;
    }

    public String hash(String password) {
        return SCHEME + BcryptUtil.bcryptHash(prepare(password), cost);
    }

    public boolean matches(String password, String storedHash) {
        if (!storedHash.startsWith(SCHEME)) {
            throw new IllegalArgumentException("Unsupported password hash scheme");
        }
        return BcryptUtil.matches(prepare(password), storedHash.substring(SCHEME.length()));
    }

    private String prepare(String password) {
        try {
            // Base64(SHA-256) preserves all input bytes before BCrypt's 72-byte limit.
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by the JVM", error);
        }
    }
}
