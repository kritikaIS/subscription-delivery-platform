package com.juiceplatform.service;

import com.juiceplatform.dto.subscription.CancelSubscriptionResponse;
import com.juiceplatform.dto.subscription.CreateSubscriptionRequest;
import com.juiceplatform.dto.subscription.PauseSubscriptionResponse;
import com.juiceplatform.dto.subscription.ResumeSubscriptionResponse;
import com.juiceplatform.dto.subscription.SubscriptionResponse;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime CUTOFF_TIME = LocalTime.of(22, 0, 0);

    private final SubscriptionRepository subscriptionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(UUID customerId, CreateSubscriptionRequest request) {
        // 1. Verify onboarding complete
        requireOnboardedCustomer(customerId);

        // 2. Verify product exists and is available
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_UNAVAILABLE",
                        "Product not found or unavailable", HttpStatus.BAD_REQUEST));

        if (!product.getIsAvailable()) {
            throw new BusinessException("PRODUCT_UNAVAILABLE",
                    "Product is currently disabled", HttpStatus.BAD_REQUEST);
        }

        // 3. Check for duplicate active subscription (BR-SUB-01)
        subscriptionRepository.findActiveByCustomerIdAndProductId(customerId, request.getProductId())
                .ifPresent(existing -> {
                    throw new BusinessException("SUBSCRIPTION_DUPLICATE",
                            "A subscription for this product already exists.", HttpStatus.CONFLICT);
                });

        // 4. Compute effective start date using cutoff rule (BR-CUT-03 / BR-CUT-04)
        LocalDate effectiveStartDate = computeEffectiveDate();

        // 5. Create subscription
        Subscription subscription = new Subscription();
        subscription.setCustomerId(customerId);
        subscription.setProductId(product.getId());
        subscription.setQuantity(request.getQuantity());
        subscription.setStartDate(effectiveStartDate);
        subscription.setStatus(Subscription.SubscriptionStatus.PENDING_START);
        subscription.setCreatedBy(customerId);
        subscription = subscriptionRepository.save(subscription);

        return toResponse(subscription, product.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionResponse> listSubscriptions(UUID customerId, String status, Pageable pageable) {
        Page<Subscription> page;

        if (status != null && !status.isBlank()) {
            Subscription.SubscriptionStatus statusEnum;
            try {
                statusEnum = Subscription.SubscriptionStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_STATUS",
                        "Invalid subscription status: " + status, HttpStatus.BAD_REQUEST);
            }
            page = subscriptionRepository.findByCustomerIdAndStatus(customerId, statusEnum, pageable);
        } else {
            page = subscriptionRepository.findByCustomerId(customerId, pageable);
        }

        return page.map(sub -> {
            String productName = productRepository.findById(sub.getProductId())
                    .map(Product::getName)
                    .orElse("Unknown Product");
            return toResponse(sub, productName);
        });
    }

    @Override
    @Transactional
    public PauseSubscriptionResponse pauseSubscription(UUID customerId, UUID subscriptionId) {
        Subscription subscription = requireOwnedSubscription(customerId, subscriptionId);

        // Validate state transition
        if (subscription.getStatus() == Subscription.SubscriptionStatus.PAUSED) {
            throw new BusinessException("SUBSCRIPTION_ALREADY_PAUSED",
                    "Subscription is already paused", HttpStatus.CONFLICT);
        }
        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new BusinessException("SUBSCRIPTION_NOT_PAUSABLE",
                    "Only ACTIVE subscriptions can be paused", HttpStatus.CONFLICT);
        }

        LocalDate effectiveDate = computeEffectiveDate();

        subscription.setStatus(Subscription.SubscriptionStatus.PAUSED);
        subscription.setPauseReason(Subscription.PauseReason.USER_PAUSED);
        subscriptionRepository.save(subscription);

        // Cancel future SCHEDULED orders from effectiveDate onward (BR-PAU-01)
        // LOCKED orders are unaffected
        cancelScheduledOrdersFrom(subscription.getId(), effectiveDate);

        return PauseSubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .status(subscription.getStatus().name())
                .pauseEffectiveDate(effectiveDate)
                .build();
    }

    @Override
    @Transactional
    public ResumeSubscriptionResponse resumeSubscription(UUID customerId, UUID subscriptionId) {
        Subscription subscription = requireOwnedSubscription(customerId, subscriptionId);

        // Validate state transition
        if (subscription.getStatus() != Subscription.SubscriptionStatus.PAUSED) {
            throw new BusinessException("SUBSCRIPTION_NOT_PAUSED",
                    "Subscription is not currently paused", HttpStatus.CONFLICT);
        }

        LocalDate effectiveDate = computeEffectiveDate();

        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setPauseReason(null);
        subscriptionRepository.save(subscription);

        return ResumeSubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .status(subscription.getStatus().name())
                .resumeEffectiveDate(effectiveDate)
                .build();
    }

    @Override
    @Transactional
    public CancelSubscriptionResponse cancelSubscription(UUID customerId, UUID subscriptionId) {
        Subscription subscription = requireOwnedSubscription(customerId, subscriptionId);

        // Validate state transition (BR-SUB-04: CANCELLED is terminal)
        if (subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
            throw new BusinessException("SUBSCRIPTION_ALREADY_CANCELLED",
                    "Subscription is already cancelled", HttpStatus.CONFLICT);
        }

        LocalDate effectiveDate = computeEffectiveDate();
        OffsetDateTime cancelledAt = OffsetDateTime.now(IST);

        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        subscription.setPauseReason(null);
        subscriptionRepository.save(subscription);

        // Cancel future SCHEDULED orders from effectiveDate onward (BR-CAN-01)
        // LOCKED orders are unaffected
        cancelScheduledOrdersFrom(subscription.getId(), effectiveDate);

        return CancelSubscriptionResponse.builder()
                .subscriptionId(subscription.getId())
                .status(subscription.getStatus().name())
                .cancelEffectiveDate(effectiveDate)
                .cancelledAt(cancelledAt)
                .build();
    }

    // --- Private helpers ---

    private void cancelScheduledOrdersFrom(UUID subscriptionId, LocalDate fromDate) {
        List<Order> scheduledOrders = orderRepository
                .findBySubscriptionIdAndStatusAndDeliveryDateGreaterThanEqual(
                        subscriptionId, Order.OrderStatus.SCHEDULED, fromDate);
        for (Order order : scheduledOrders) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            orderRepository.save(order);
        }
    }

    private User requireOnboardedCustomer(UUID customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + customerId));

        if (!customer.getIsActive()) {
            throw new BusinessException("ACCOUNT_DEACTIVATED",
                    "Account is deactivated", HttpStatus.FORBIDDEN);
        }
        if (!customer.getOnboardingCompleted()) {
            throw new BusinessException("ONBOARDING_INCOMPLETE",
                    "Customer has not completed onboarding", HttpStatus.FORBIDDEN);
        }
        return customer;
    }

    private Subscription requireOwnedSubscription(UUID customerId, UUID subscriptionId) {
        return subscriptionRepository.findByIdAndCustomerId(subscriptionId, customerId)
                .orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND",
                        "Subscription not found", HttpStatus.NOT_FOUND));
    }

    private LocalDate computeEffectiveDate() {
        // BR-CUT-01/03/04: cutoff at 22:00 IST
        OffsetDateTime nowIst = OffsetDateTime.now(IST);
        LocalTime currentTime = nowIst.toLocalTime();

        if (currentTime.isBefore(CUTOFF_TIME)) {
            // Before 22:00 → effective date is tomorrow
            return nowIst.toLocalDate().plusDays(1);
        } else {
            // At or after 22:00 → effective date is day-after-tomorrow
            return nowIst.toLocalDate().plusDays(2);
        }
    }

    private SubscriptionResponse toResponse(Subscription subscription, String productName) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .productId(subscription.getProductId())
                .productName(productName)
                .quantity(subscription.getQuantity())
                .status(subscription.getStatus().name())
                .effectiveStartDate(subscription.getStartDate())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
