package com.juiceplatform.service;

import com.juiceplatform.dto.wallet.RechargeRequestBody;
import com.juiceplatform.dto.wallet.RechargeRequestResponse;
import com.juiceplatform.entity.RechargeRequestLog;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.RechargeRequestLogRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Handles wallet recharge requests with database-backed rate limiting.
 *
 * Per API spec §6.3:
 * - Stateless from a business perspective: no wallet mutation, no audit log (BR-NOT-02)
 * - Rate-limited: one request per customer per hour → 429
 * - Rate limit is database-backed (persistent across restarts and instances)
 *
 * Per BR-NOT-01: notification is best-effort and non-blocking.
 */
@Service
@RequiredArgsConstructor
public class WalletRechargeRequestService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long RATE_LIMIT_WINDOW_HOURS = 1L;

    private final RechargeRequestLogRepository rechargeRequestLogRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    /**
     * Processes a wallet recharge request.
     * Rate-limited to one request per customer per hour (API spec §6.3).
     * Uses database-backed rate limiting for persistence across restarts.
     */
    @Transactional
    public RechargeRequestResponse requestRecharge(UUID customerId, RechargeRequestBody body) {
        OffsetDateTime now = OffsetDateTime.now(IST);

        // Database-backed rate limit check (API spec §6.3: at most one request per hour)
        rechargeRequestLogRepository.findById(customerId).ifPresent(existing -> {
            OffsetDateTime windowStart = now.minusHours(RATE_LIMIT_WINDOW_HOURS);
            if (existing.getLastRequestedAt().isAfter(windowStart)) {
                throw new BusinessException("RATE_LIMIT_EXCEEDED",
                        "You can only request a wallet recharge once per hour",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
        });

        // Upsert the rate limit record
        RechargeRequestLog logEntry = new RechargeRequestLog(customerId, now);
        rechargeRequestLogRepository.save(logEntry);

        // Load customer for notification context
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Customer not found", HttpStatus.NOT_FOUND));

        long currentBalance = walletService.getCurrentBalance(customerId);

        // Notify admin — best-effort, non-blocking (BR-NOT-01)
        // No audit log, no wallet ledger entry (BR-NOT-02)
        notificationService.notifyAdminWalletRechargeRequested(
                customerId, customer.getName(), body.getNotes(), currentBalance);

        return RechargeRequestResponse.builder()
                .status("REQUESTED")
                .requestedAt(now)
                .build();
    }
}
