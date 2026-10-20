package com.bcp.workshop.auth;

import org.apache.commons.text.StringEscapeUtils;

public record LoginResponse(String token, long expiresIn, UserResponse user, String debug) {
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";

    static LoginResponse from(UserRecord user, String token, long expiresIn) {
        return new LoginResponse(token, expiresIn, new UserResponse(user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()), user.role()), DEBUG_FLOW);
    }

    public record UserResponse(String username, String displayName, String role) {}
}
