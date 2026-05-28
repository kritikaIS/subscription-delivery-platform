package com.juiceplatform.security;

import java.util.UUID;

public interface JwtService {

    String generateAccessToken(UUID userId, String role, String phone);

    boolean isTokenValid(String token);

    AuthenticatedUser extractUser(String token);
}
