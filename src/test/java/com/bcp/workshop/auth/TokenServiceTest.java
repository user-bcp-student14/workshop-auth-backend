package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    @TempDir(factory = DatabaseFixture.DirectoryFactory.class)
    Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant instant = Instant.parse("2026-10-19T12:00:00Z");
    private final UserRecord user = new UserRecord(1, DatabaseFixture.STUDENT, "Test", "STUDENT");

    @Test
    void signsClaimsWithExactLifetimeAndIndependentVerifier() throws Exception {
        var keys = keys(2048);
        var service = new TokenService(keys.getPrivate(), Clock.fixed(instant, ZoneOffset.UTC), "test-issuer", "test-audience");
        String token = service.create(user);
        String[] segments = token.split("\\.");
        assertEquals(3, segments.length);
        assertTrue(verifies(token, keys.getPublic()));
        JsonNode header = decode(segments[0]);
        JsonNode claims = decode(segments[1]);
        assertEquals("RS256", header.get("alg").asText());
        assertEquals(user.username(), claims.get("sub").asText());
        assertEquals(user.role(), claims.get("role").asText());
        assertEquals("test-issuer", claims.get("iss").asText());
        assertTrue(claims.get("aud").toString().contains("test-audience"));
        assertEquals(instant.getEpochSecond(), claims.get("iat").asLong());
        assertEquals(TokenService.LIFETIME_SECONDS, claims.get("exp").asLong() - claims.get("iat").asLong());
        assertFalse(claims.has("secret"));
        assertFalse(claims.has("password"));
        assertFalse(claims.has("password_hash"));
        assertTrue(validAt(token, instant, "test-issuer", "test-audience"));
        assertFalse(validAt(token, instant.plusSeconds(TokenService.LIFETIME_SECONDS), "test-issuer", "test-audience"));
        assertFalse(validAt(token, instant, "wrong", "test-audience"));
        assertFalse(validAt(token, instant, "test-issuer", "wrong"));
        assertFalse(verifies(token, keys(2048).getPublic()));
        String alteredClaims = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8)
                .replace("STUDENT", "TRAINER");
        String altered = segments[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(alteredClaims.getBytes(StandardCharsets.UTF_8)) + "." + segments[2];
        assertFalse(verifies(altered, keys.getPublic()));
    }

    @Test
    void loadsExternalKeyAndRejectsMissingMalformedOrWeakConfiguration() throws Exception {
        Path key = directory.resolve("key.pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keys(2048).getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----");
        assertEquals(3, new TokenService(key.toString(), "test-issuer", "test-audience").create(user).split("\\.").length);
        assertThrows(IllegalStateException.class,
                () -> new TokenService(directory.resolve("missing").toString(), "test-issuer", "test-audience"));
        Files.writeString(key, "invalid-key");
        assertThrows(IllegalStateException.class, () -> new TokenService(key.toString(), "test-issuer", "test-audience"));
        assertThrows(IllegalArgumentException.class, () -> new TokenService("", "test-issuer", "test-audience"));
        var weak = keys(1024);
        assertThrows(IllegalArgumentException.class,
                () -> new TokenService(weak.getPrivate(), Clock.systemUTC(), "test-issuer", "test-audience"));
        var strong = keys(2048);
        assertThrows(IllegalArgumentException.class,
                () -> new TokenService(strong.getPrivate(), Clock.systemUTC(), " ", "test-audience"));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenService(strong.getPrivate(), Clock.systemUTC(), "test-issuer", null));
    }

    private java.security.KeyPair keys(int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private JsonNode decode(String segment) throws Exception {
        return mapper.readTree(Base64.getUrlDecoder().decode(segment));
    }

    private boolean verifies(String token, PublicKey key) throws Exception {
        String[] segments = token.split("\\.");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(key);
        signature.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        return signature.verify(Base64.getUrlDecoder().decode(segments[2]));
    }

    private boolean validAt(String token, Instant now, String issuer, String audience) throws Exception {
        JsonNode claims = decode(token.split("\\.")[1]);
        return claims.get("exp").asLong() > now.getEpochSecond()
                && claims.get("iss").asText().equals(issuer) && claims.get("aud").toString().contains(audience);
    }
}
