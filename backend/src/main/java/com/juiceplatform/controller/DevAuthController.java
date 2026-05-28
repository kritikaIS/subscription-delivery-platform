package com.juiceplatform.controller;

import com.juiceplatform.dto.auth.AuthResponse;
import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.entity.RefreshToken;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.RefreshTokenRepository;
import com.juiceplatform.repository.UserRepository;
import com.juiceplatform.security.JwtService;
import com.juiceplatform.service.OrderFreezeService;
import com.juiceplatform.service.OrderGenerationService;
import com.juiceplatform.service.SubscriptionActivationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * TEMPORARY development-only controller for generating test customer tokens.
 * Active ONLY when Spring profile "dev" is active.
 * Remove before production deployment.
 */
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
@Profile("dev")
public class DevAuthController {

    private static final UUID TEST_CUSTOMER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String TEST_CUSTOMER_ROLE = "CUSTOMER";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final OrderGenerationService orderGenerationService;
    private final SubscriptionActivationService subscriptionActivationService;
    private final OrderFreezeService orderFreezeService;

    @Value("${jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    @PostConstruct
    public void init() {
        System.out.println(">>> DevAuthController loaded and active");
    }

    @PostMapping("/token/customer")
    public ResponseEntity<ApiResponse<AuthResponse>> generateTestCustomerToken() {
        // Generate access token for the seeded test customer
        String accessToken = jwtService.generateAccessToken(TEST_CUSTOMER_ID, TEST_CUSTOMER_ROLE, null);

        // Generate and persist refresh token
        String rawRefreshToken = generateSecureToken();
        persistRefreshToken(TEST_CUSTOMER_ID, rawRefreshToken);

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/token/admin")
    public ResponseEntity<ApiResponse<AuthResponse>> generateTestAdminToken() {
        User admin = userRepository.findByPhone("9999999999")
                .orElseThrow(() -> new IllegalStateException("Test admin not found — ensure V100 seed migration ran"));

        String accessToken = jwtService.generateAccessToken(admin.getId(), admin.getRole().name(), admin.getPhone());
        String rawRefreshToken = generateSecureToken();
        persistRefreshToken(admin.getId(), rawRefreshToken);

        return ResponseEntity.ok(ApiResponse.success(AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .build()));
    }

    @PostMapping("/orders/generate")
    public ResponseEntity<ApiResponse<OrderGenerationService.OrderGenerationResult>> triggerOrderGeneration() {
        java.time.LocalDate deliveryDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1);
        OrderGenerationService.OrderGenerationResult result = orderGenerationService.generateOrdersForDate(deliveryDate);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/subscriptions/activate")
    public ResponseEntity<ApiResponse<SubscriptionActivationService.ActivationResult>> triggerSubscriptionActivation() {
        SubscriptionActivationService.ActivationResult result =
                subscriptionActivationService.activateEligibleSubscriptions();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/orders/freeze")
    public ResponseEntity<ApiResponse<OrderFreezeService.FreezeResult>> triggerOrderFreeze() {
        java.time.LocalDate deliveryDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1);
        OrderFreezeService.FreezeResult result = orderFreezeService.freezeOrdersForDate(deliveryDate);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private void persistRefreshToken(UUID userId, String rawToken) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setExpiresAt(OffsetDateTime.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
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
