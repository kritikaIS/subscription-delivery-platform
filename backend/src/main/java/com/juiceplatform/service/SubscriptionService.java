package com.juiceplatform.service;

import com.juiceplatform.dto.subscription.CancelSubscriptionResponse;
import com.juiceplatform.dto.subscription.CreateSubscriptionRequest;
import com.juiceplatform.dto.subscription.PauseSubscriptionResponse;
import com.juiceplatform.dto.subscription.ResumeSubscriptionResponse;
import com.juiceplatform.dto.subscription.SubscriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionService {

    SubscriptionResponse createSubscription(UUID customerId, CreateSubscriptionRequest request);

    Page<SubscriptionResponse> listSubscriptions(UUID customerId, String status, Pageable pageable);

    PauseSubscriptionResponse pauseSubscription(UUID customerId, UUID subscriptionId);

    ResumeSubscriptionResponse resumeSubscription(UUID customerId, UUID subscriptionId);

    CancelSubscriptionResponse cancelSubscription(UUID customerId, UUID subscriptionId);
}
