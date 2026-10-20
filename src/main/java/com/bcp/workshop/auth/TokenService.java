package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;

@Startup
@ApplicationScoped
public class TokenService {
    public static final long LIFETIME_SECONDS = 3600;
    private static final int MIN_RSA_BITS = 2048;
    private static final Logger LOG = Logger.getLogger(TokenService.class);
    private final PrivateKey privateKey;
    private final Clock clock;
    private final String issuer;
    private final String audience;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.private-key-path") String keyPath,
                        @ConfigProperty(name = "auth.token.issuer") String issuer,
                        @ConfigProperty(name = "auth.token.audience") String audience) {
        this(loadKey(keyPath), Clock.systemUTC(), issuer, audience);
    }

    TokenService(PrivateKey privateKey, Clock clock, String issuer, String audience) {
        if (!(privateKey instanceof RSAPrivateKey rsaKey) || rsaKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalArgumentException("JWT signing requires an RSA private key of at least 2048 bits");
        }
        this.privateKey = privateKey;
        this.clock = clock;
        this.issuer = requireValue(issuer, "JWT issuer");
        this.audience = requireValue(audience, "JWT audience");
    }

    public String create(UserRecord user) {
        long issuedAt = clock.instant().getEpochSecond();
        return Jwt.issuer(issuer).audience(audience).subject(user.username())
                .claim("role", user.role()).issuedAt(issuedAt).expiresAt(issuedAt + LIFETIME_SECONDS)
                .jws().algorithm(SignatureAlgorithm.RS256).sign(privateKey);
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value;
    }

    private static PrivateKey loadKey(String keyPath) {
        requireValue(keyPath, "JWT private key path");
        try {
            String pem = Files.readString(Path.of(keyPath));
            byte[] encoded = Base64.getDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException error) {
            LOG.error("Unable to load JWT signing key");
            throw new IllegalStateException("Unable to load JWT signing key", error);
        }
    }
}
