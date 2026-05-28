package com.juiceplatform.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Verifies Google ID tokens server-side (BR-AUTH-02).
 *
 * Validates:
 * - Token signature (against Google's public keys fetched from JWKS endpoint)
 * - Token expiry (exp claim)
 * - Issuer (must be accounts.google.com or https://accounts.google.com)
 * - Audience (must match configured GOOGLE_CLIENT_ID)
 *
 * If any validation fails, returns null — caller must reject authentication.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Google Client ID is not configured. Set the GOOGLE_CLIENT_ID environment variable.");
        }

        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .setIssuer("https://accounts.google.com")
                .build();
    }

    /**
     * Verifies the Google ID token and returns the payload.
     * Returns null if the token is invalid, expired, has wrong audience, or wrong issuer.
     */
    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.debug("Google ID token verification failed: token is invalid, expired, or has wrong audience/issuer");
                return null;
            }
            return idToken.getPayload();
        } catch (Exception e) {
            log.debug("Google ID token verification error: {}", e.getMessage());
            return null;
        }
    }
}
