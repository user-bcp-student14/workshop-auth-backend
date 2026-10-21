package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigurationTest {
    @Test
    void requiresAnExplicitAllowlist() {
        assertDoesNotThrow(() -> new CorsConfiguration("https://client.example.test,http://localhost:4200"));
        for (String invalid : new String[]{"", " ", "*", "/.*/", "null", "ftp://example.test",
                "https://example.test/path", "https://example.test/", "https://user@example.test",
                "https://example.test?q=test", "https://example.test#fragment", "https://example.test:0",
                "https://example.test:99999", "https://example.test,", "https://bad host"}) {
            assertThrows(IllegalArgumentException.class, () -> new CorsConfiguration(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new CorsConfiguration(null));
    }
}
