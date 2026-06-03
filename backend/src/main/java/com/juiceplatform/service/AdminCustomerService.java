package com.juiceplatform.service;

import com.juiceplatform.dto.customer.AdminCustomerResponse;
import com.juiceplatform.entity.User;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.UserRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only admin query service for customers.
 * Filters out ADMIN accounts so they never appear in the customer list.
 */
@Service
@RequiredArgsConstructor
public class AdminCustomerService {

    private final UserRepository userRepository;
    private final WalletLedgerRepository walletLedgerRepository;

    @Transactional(readOnly = true)
    public Page<AdminCustomerResponse> getAllCustomers(Pageable pageable) {
        // Use role filter to exclude the admin account (BR-SEC: Admin leakage prevention)
        return userRepository.findByRole(User.UserRole.CUSTOMER, pageable)
                .map(user -> mapToDto(user, fetchBalance(user.getId())));
    }

    @Transactional(readOnly = true)
    public AdminCustomerResponse getCustomerDetail(UUID customerId) {
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                        "Customer not found", HttpStatus.NOT_FOUND));

        return mapToDto(user, fetchBalance(customerId));
    }

    private long fetchBalance(UUID customerId) {
        return walletLedgerRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .map(WalletLedger::getRunningBalancePaise)
                .orElse(0L);
    }

    private AdminCustomerResponse mapToDto(User user, long walletBalancePaise) {
        return AdminCustomerResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .onboardingComplete(user.getOnboardingCompleted())
                .walletBalancePaise(walletBalancePaise)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
