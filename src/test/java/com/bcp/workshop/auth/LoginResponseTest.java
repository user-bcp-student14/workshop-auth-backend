package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginResponseTest {
    @Test
    void preservesExactHtml4EscapingIncludingUnicodeAndNull() {
        String[][] examples = {
                {"&", "&amp;"},
                {"<", "&lt;"},
                {">", "&gt;"},
                {"\"", "&quot;"},
                {"'", "'"},
                {"Pe\u00f1a \u4e2d \ud83d\ude00", "Pe&ntilde;a \u4e2d \ud83d\ude00"},
                {null, null},
                {"<Test & \"Student\">'", "&lt;Test &amp; &quot;Student&quot;&gt;'"}
        };
        for (String[] example : examples) {
            UserRecord user = new UserRecord(42, "test-user", example[0], "STUDENT");
            assertEquals(example[1], LoginResponse.from(user, "test-token", 3600).user().displayName());
        }
    }

    @Test
    void mapsOnlyPublicIdentityAndPreservesResponseFields() {
        UserRecord user = new UserRecord(42, "test-user", "Test", "TRAINER");
        LoginResponse response = LoginResponse.from(user, "test-token", 3600);
        assertEquals(new LoginResponse("test-token", 3600,
                new LoginResponse.UserResponse("test-user", "Test", "TRAINER"),
                "authenticated-with-legacy-flow"), response);
    }
}
