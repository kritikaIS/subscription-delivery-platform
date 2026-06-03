package com.juiceplatform.service;

import com.juiceplatform.dto.subscription.AdminSubscriptionResponse;
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
import com.juiceplatform.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionService {

    SubscriptionResponse createSubscription(UUID customerId, CreateSubscriptionRequest request);

    Page<SubscriptionResponse> listSubscriptions(UUID customerId, String status, Pageable pageable);

    SubscriptionDetailResponse getSubscriptionDetail(UUID customerId, UUID subscriptionId);

    PauseSubscriptionResponse pauseSubscription(UUID customerId, UUID subscriptionId);

    ResumeSubscriptionResponse resumeSubscription(UUID customerId, UUID subscriptionId);

    CancelSubscriptionResponse cancelSubscription(UUID customerId, UUID subscriptionId);

    QuantityChangeResponse changeQuantity(UUID customerId, UUID subscriptionId, ChangeQuantityRequest request);

    ProductChangeResponse changeProduct(UUID customerId, UUID subscriptionId, ChangeProductRequest request);

    Page<ChangeRequestListEntry> listChangeRequests(UUID customerId, UUID subscriptionId,
                                                    String type, String status, Pageable pageable);

    // Admin read methods
    Page<AdminSubscriptionResponse> getAllSubscriptions(Pageable pageable);
}

