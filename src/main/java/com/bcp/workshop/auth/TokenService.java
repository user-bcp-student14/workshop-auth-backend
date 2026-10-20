package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Startup
@ApplicationScoped
public class TokenService {
    public static final long LIFETIME_SECONDS = 3600;
    private static final int MIN_RSA_BITS = 2048;
    private static final Logger LOG = Logger.getLogger(TokenService.class);
    private final RSAPrivateCrtKey privateKey;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final String keyId;

    @Inject
    public TokenService(@ConfigProperty(name = "auth.token.private-key-path") String keyPath,
                        @ConfigProperty(name = "auth.token.issuer") String issuer,
                        @ConfigProperty(name = "auth.token.audience") String audience) {
        this(loadKey(keyPath), Clock.systemUTC(), issuer, audience);
    }

    TokenService(PrivateKey privateKey, Clock clock, String issuer, String audience) {
        if (!(privateKey instanceof RSAPrivateCrtKey rsaKey) || rsaKey.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalArgumentException("JWT signing requires an RSA private key of at least 2048 bits");
        }
        this.privateKey = rsaKey;
        this.clock = clock;
        this.issuer = requireValue(issuer, "JWT issuer");
        this.audience = requireValue(audience, "JWT audience");
        this.keyId = keyId(rsaKey);
    }

    public String create(UserRecord user) {
        long issuedAt = clock.instant().getEpochSecond();
        return Jwt.issuer(issuer).audience(audience).subject(user.username())
                .claim("role", user.role()).issuedAt(issuedAt).expiresAt(issuedAt + LIFETIME_SECONDS)
                .jws().keyId(keyId).algorithm(SignatureAlgorithm.RS256).sign(privateKey);
    }

    public JwkSet publicJwks() {
        return new JwkSet(List.of(new JsonWebKey("RSA", "sig", "RS256", keyId,
                base64UrlUnsigned(privateKey.getModulus()), base64UrlUnsigned(privateKey.getPublicExponent()))));
    }

    private static String keyId(RSAPrivateCrtKey key) {
        String modulus = base64UrlUnsigned(key.getModulus());
        String exponent = base64UrlUnsigned(key.getPublicExponent());
        String thumbprintInput = "{\"e\":\"" + exponent + "\",\"kty\":\"RSA\",\"n\":\"" + modulus + "\"}";
        try {
            byte[] thumbprint = MessageDigest.getInstance("SHA-256")
                    .digest(thumbprintInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int firstByte = bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Arrays.copyOfRange(bytes, firstByte, bytes.length));
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

    public record JwkSet(List<JsonWebKey> keys) {
    }

    public record JsonWebKey(String kty, String use, String alg, String kid, String n, String e) {
    }
}
