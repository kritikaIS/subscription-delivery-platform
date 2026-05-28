package com.juiceplatform.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.juiceplatform.dto.auth.AdminLoginRequest;
import com.juiceplatform.dto.auth.AuthResponse;
import com.juiceplatform.dto.auth.CustomerGoogleLoginRequest;
import com.juiceplatform.dto.auth.CustomerLoginResponse;
import com.juiceplatform.dto.auth.RefreshTokenRequest;
import com.juiceplatform.entity.AdminCredentials;
import com.juiceplatform.entity.RefreshToken;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.AuthenticationFailedException;
import com.juiceplatform.repository.AdminCredentialsRepository;
import com.juiceplatform.repository.RefreshTokenRepository;
import com.juiceplatform.repository.UserRepository;
import com.juiceplatform.security.GoogleTokenVerifier;
import com.juiceplatform.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final AdminCredentialsRepository adminCredentialsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;

    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    @Override
    @Transactional
    public CustomerLoginResponse customerGoogleLogin(CustomerGoogleLoginRequest request) {
        // 1. Verify Google ID token server-side (BR-AUTH-02)
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.getIdToken());
        if (payload == null) {
            throw new AuthenticationFailedException("Invalid Google ID token");
        }

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        // 2. Find or create customer user
        User customer = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> createCustomerUser(googleId, email, name));

        // 3. Check account is active (BR-ACC-02)
        if (!customer.getIsActive()) {
            throw new AuthenticationFailedException("Account is deactivated");
        }

        // 4. Revoke all existing refresh tokens (BR-AUTH-04: single session)
        refreshTokenRepository.revokeAllByUserId(customer.getId());

        // 4. Generate tokens
        String accessToken = jwtService.generateAccessToken(
                customer.getId(), customer.getRole().name(), customer.getPhone());
        String rawRefreshToken = generateSecureToken();

        // 5. Persist hashed refresh token
        persistRefreshToken(customer.getId(), rawRefreshToken);

        return CustomerLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .customerId(customer.getId())
                .onboardingComplete(customer.getOnboardingCompleted())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse adminLogin(AdminLoginRequest request) {
        // 1. Find user by phone (simple identity lookup)
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid phone or password"));

        // 2. Validate role is ADMIN (business logic in service layer)
        if (user.getRole() != User.UserRole.ADMIN) {
            throw new AuthenticationFailedException("Invalid phone or password");
        }

        // 3. Verify admin credentials exist
        AdminCredentials credentials = adminCredentialsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid phone or password"));

        // 4. Verify password
        if (!passwordEncoder.matches(request.getPassword(), credentials.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid phone or password");
        }

        // 5. Revoke all existing refresh tokens for this user (BR-AUTH-04)
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // 6. Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name(), user.getPhone());
        String rawRefreshToken = generateSecureToken();

        // 7. Persist hashed refresh token
        persistRefreshToken(user.getId(), rawRefreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = hashToken(rawToken);

        // 1. Find non-revoked token by hash
        RefreshToken storedToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or revoked refresh token"));

        // 2. Check expiry
        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AuthenticationFailedException("Refresh token expired");
        }

        // 3. Revoke the current refresh token (rotation)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // 4. Load user
        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        // 5. Check account is still active
        if (!user.getIsActive()) {
            throw new AuthenticationFailedException("Account is deactivated");
        }

        // 6. Generate new token pair
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name(), user.getPhone());
        String newRawRefreshToken = generateSecureToken();

        // 6. Persist new hashed refresh token
        persistRefreshToken(user.getId(), newRawRefreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRawRefreshToken)
                .build();
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();
        String tokenHash = hashToken(rawToken);

        // Find non-revoked token by hash and revoke it
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });

        // Always return success — do not reveal whether the token existed
    }

    private User createCustomerUser(String googleId, String email, String name) {
        User user = new User();
        user.setName(name != null ? name : "Customer");
        user.setEmail(email);
        user.setGoogleId(googleId);
        user.setRole(User.UserRole.CUSTOMER);
        user.setAuthProvider(User.AuthProvider.GOOGLE);
        user.setEmailVerified(true);
        user.setOnboardingCompleted(false);
        user.setIsActive(true);
        return userRepository.save(user);
    }

    private void persistRefreshToken(UUID userId, String rawToken) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiresAt(OffsetDateTime.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
