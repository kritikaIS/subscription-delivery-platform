package com.juiceplatform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PHONE = "phone";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMinutes;

    public JwtServiceImpl(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-minutes}") long accessTokenExpiryMinutes) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured. Set the JWT_SECRET environment variable.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters (256 bits) for HS256 signing.");
        }

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    @Override
    public String generateAccessToken(UUID userId, String role, String phone) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_PHONE, phone)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public AuthenticatedUser extractUser(String token) {
        Claims claims = parseClaims(token);

        UUID userId = UUID.fromString(claims.get(CLAIM_USER_ID, String.class));
        String role = claims.get(CLAIM_ROLE, String.class);
        String phone = claims.get(CLAIM_PHONE, String.class);

        return new AuthenticatedUser(userId, role, phone);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
