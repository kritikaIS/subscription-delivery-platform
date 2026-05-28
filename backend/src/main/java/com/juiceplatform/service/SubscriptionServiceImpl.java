package com.juiceplatform.service;

import com.juiceplatform.dto.subscription.CancelSubscriptionResponse;
import com.juiceplatform.dto.subscription.ChangeProductRequest;
import com.juiceplatform.dto.subscription.ChangeQuantityRequest;
import com.juiceplatform.dto.subscription.ChangeRequestListEntry;
import com.juiceplatform.dto.subscription.CreateSubscriptionRequest;
import com.juiceplatform.dto.subscription.PauseSubscriptionResponse;
import com.juiceplatform.dto.subscription.ProductChangeResponse;
import com.juiceplatform.dto.subscription.QuantityChangeResponse;
import com.juiceplatform.dto.subscription.ResumeSubscriptionResponse;
import com.juiceplatform.dto.subscription.SubscriptionDetailResponse;
import com.juiceplatform.dto.subscription.SubscriptionResponse;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.SubscriptionChangeRequest;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.SubscriptionChangeRequestRepository;
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
import java.util.ArrayList;
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
    private final SubscriptionChangeRequestRepository changeRequestRepository;

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

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDetailResponse getSubscriptionDetail(UUID customerId, UUID subscriptionId) {
        Subscription subscription = requireOwnedSubscription(customerId, subscriptionId);

        String productName = productRepository.findById(subscription.getProductId())
                .map(Product::getName)
                .orElse("Unknown Product");

        // Fetch APPROVED change requests for the pending list
        List<SubscriptionChangeRequest> approvedRequests = changeRequestRepository
                .findBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                        subscriptionId,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED,
                        Pageable.unpaged())
                .getContent();

        List<SubscriptionDetailResponse.PendingChangeEntry> pendingEntries = new ArrayList<>();
        for (SubscriptionChangeRequest req : approvedRequests) {
            SubscriptionDetailResponse.PendingChangeEntry.PendingChangeEntryBuilder builder =
                    SubscriptionDetailResponse.PendingChangeEntry.builder()
                            .type(req.getChangeType().name())
                            .status(req.getStatus().name())
                            .effectiveDate(req.getEffectiveDate());

            if (req.getChangeType() == SubscriptionChangeRequest.ChangeRequestType.QUANTITY) {
                builder.newQuantity(Integer.parseInt(req.getNewValue()));
            } else {
                UUID newProductId = UUID.fromString(req.getNewValue());
                String newProductName = productRepository.findById(newProductId)
                        .map(Product::getName)
                        .orElse("Unknown Product");
                builder.newProductId(newProductId).newProductName(newProductName);
            }
            pendingEntries.add(builder.build());
        }

        return SubscriptionDetailResponse.builder()
                .id(subscription.getId())
                .productId(subscription.getProductId())
                .productName(productName)
                .quantity(subscription.getQuantity())
                .status(subscription.getStatus().name())
                .effectiveStartDate(subscription.getStartDate())
                .pendingChangeRequests(pendingEntries)
                .createdAt(subscription.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public QuantityChangeResponse changeQuantity(UUID customerId, UUID subscriptionId,
                                                  ChangeQuantityRequest request) {
        Subscription subscription = requireOwnedSubscription(customerId, subscriptionId);
        requireModifiableSubscription(subscription);

        LocalDate effectiveDate = computeEffectiveDate();

        // Supersede any existing APPROVED QUANTITY request (BR-SUB-08)
        List<SubscriptionChangeRequest> existing = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        subscriptionId,
                        SubscriptionChangeRequest.ChangeRequestType.QUANTITY,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        for (SubscriptionChangeRequest old : existing) {
            old.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.SUPERSEDED);
            changeRequestRepository.save(old);
        }

        // Create new APPROVED request (BR-SUB-09)
        SubscriptionChangeRequest changeRequest = new SubscriptionChangeRequest();
        changeRequest.setSubscriptionId(subscriptionId);
        changeRequest.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        changeRequest.setNewValue(String.valueOf(request.getNewQuantity()));
        changeRequest.setEffectiveDate(effectiveDate);
        changeRequest.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        changeRequest.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        changeRequest.setRequestedByUserId(customerId);
        changeRequest = changeRequestRepository.save(changeRequest);

        return QuantityChangeResponse.builder()
                .changeRequestId(changeRequest.getId())
                .type(changeRequest.getChangeType().name())
                .newQuantity(request.getNewQuantity())
                .status(changeRequest.getStatus().name())
                .effectiveDate(effectiveDate)
                .build();
    }

    @Override
    @Transactional
    public ProductChangeResponse changeProduct(UUID customerId, UUID subscriptionId,
                                                ChangeProductRequest request) {
        Subscription subscription = requireOwnedSubscription(customerId, subscriptionId);
        requireModifiableSubscription(subscription);

        // Validate target product exists and is available
        Product newProduct = productRepository.findById(request.getNewProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_UNAVAILABLE",
                        "Product not found or unavailable", HttpStatus.BAD_REQUEST));
        if (!newProduct.getIsAvailable()) {
            throw new BusinessException("PRODUCT_UNAVAILABLE",
                    "Product is currently disabled", HttpStatus.BAD_REQUEST);
        }

        // Check that the customer doesn't already have an active subscription for the target product
        // (excluding the current subscription being changed)
        subscriptionRepository.findActiveByCustomerIdAndProductId(customerId, request.getNewProductId())
                .ifPresent(existing -> {
                    // Only reject if it's a different subscription
                    if (!existing.getId().equals(subscriptionId)) {
                        throw new BusinessException("SUBSCRIPTION_DUPLICATE",
                                "A subscription for this product already exists.", HttpStatus.CONFLICT);
                    }
                });

        LocalDate effectiveDate = computeEffectiveDate();

        // Supersede any existing APPROVED PRODUCT request (BR-SUB-08)
        List<SubscriptionChangeRequest> existingRequests = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        subscriptionId,
                        SubscriptionChangeRequest.ChangeRequestType.PRODUCT,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        for (SubscriptionChangeRequest old : existingRequests) {
            old.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.SUPERSEDED);
            changeRequestRepository.save(old);
        }

        // Create new APPROVED request (BR-SUB-09)
        SubscriptionChangeRequest changeRequest = new SubscriptionChangeRequest();
        changeRequest.setSubscriptionId(subscriptionId);
        changeRequest.setChangeType(SubscriptionChangeRequest.ChangeRequestType.PRODUCT);
        changeRequest.setNewValue(request.getNewProductId().toString());
        changeRequest.setEffectiveDate(effectiveDate);
        changeRequest.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        changeRequest.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        changeRequest.setRequestedByUserId(customerId);
        changeRequest = changeRequestRepository.save(changeRequest);

        return ProductChangeResponse.builder()
                .changeRequestId(changeRequest.getId())
                .type(changeRequest.getChangeType().name())
                .newProductId(newProduct.getId())
                .newProductName(newProduct.getName())
                .status(changeRequest.getStatus().name())
                .effectiveDate(effectiveDate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChangeRequestListEntry> listChangeRequests(UUID customerId, UUID subscriptionId,
                                                            String type, String status,
                                                            Pageable pageable) {
        // Ownership check
        requireOwnedSubscription(customerId, subscriptionId);

        SubscriptionChangeRequest.ChangeRequestType typeEnum = null;
        if (type != null && !type.isBlank()) {
            try {
                typeEnum = SubscriptionChangeRequest.ChangeRequestType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_TYPE",
                        "Invalid change request type: " + type, HttpStatus.BAD_REQUEST);
            }
        }

        SubscriptionChangeRequest.ChangeRequestStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = SubscriptionChangeRequest.ChangeRequestStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_STATUS",
                        "Invalid change request status: " + status, HttpStatus.BAD_REQUEST);
            }
        }

        Page<SubscriptionChangeRequest> page;
        if (typeEnum != null && statusEnum != null) {
            page = changeRequestRepository.findBySubscriptionIdAndChangeTypeAndStatusOrderByCreatedAtDesc(
                    subscriptionId, typeEnum, statusEnum, pageable);
        } else if (typeEnum != null) {
            page = changeRequestRepository.findBySubscriptionIdAndChangeTypeOrderByCreatedAtDesc(
                    subscriptionId, typeEnum, pageable);
        } else if (statusEnum != null) {
            page = changeRequestRepository.findBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                    subscriptionId, statusEnum, pageable);
        } else {
            page = changeRequestRepository.findBySubscriptionIdOrderByCreatedAtDesc(
                    subscriptionId, pageable);
        }

        return page.map(req -> toChangeRequestListEntry(req));
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

    private ChangeRequestListEntry toChangeRequestListEntry(SubscriptionChangeRequest req) {
        ChangeRequestListEntry.ChangeRequestListEntryBuilder builder = ChangeRequestListEntry.builder()
                .id(req.getId())
                .type(req.getChangeType().name())
                .status(req.getStatus().name())
                .effectiveDate(req.getEffectiveDate())
                .requestedBy(req.getRequestedByType().name())
                .createdAt(req.getCreatedAt());

        if (req.getChangeType() == SubscriptionChangeRequest.ChangeRequestType.QUANTITY) {
            builder.newQuantity(Integer.parseInt(req.getNewValue()));
        } else {
            UUID newProductId = UUID.fromString(req.getNewValue());
            String newProductName = productRepository.findById(newProductId)
                    .map(Product::getName)
                    .orElse("Unknown Product");
            builder.newProductId(newProductId).newProductName(newProductName);
        }

        return builder.build();
    }

    /**
     * Validates that a subscription is in a state that allows change requests (BR-SUB-07).
     * Only ACTIVE and PAUSED subscriptions may have change requests.
     */
    private void requireModifiableSubscription(Subscription subscription) {
        if (subscription.getStatus() == Subscription.SubscriptionStatus.PENDING_START
                || subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED) {
            throw new BusinessException("SUBSCRIPTION_NOT_MODIFIABLE",
                    "Changes are not allowed before subscription activation", HttpStatus.CONFLICT);
        }
    }
}
