package com.flagship.server.dto;

import java.util.List;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> roles
) {
}
