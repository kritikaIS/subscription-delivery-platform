package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.subscription.CreateSubscriptionRequest;
import com.juiceplatform.dto.subscription.SubscriptionResponse;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class SubscriptionServiceTest extends AbstractIntegrationTest {

    @Autowired SubscriptionService subscriptionService;
    @Autowired TestDataFactory factory;
    @Autowired SubscriptionRepository subscriptionRepository;

    User customer;
    Product product;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        product = factory.createProduct(2500L);
    }

    @Test
    void createSubscription_happyPath_returnsPendingStart() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(product.getId(), 2);
        SubscriptionResponse response = subscriptionService.createSubscription(customer.getId(), request);

        assertThat(response.getStatus()).isEqualTo("PENDING_START");
        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getProductId()).isEqualTo(product.getId());
        assertThat(response.getEffectiveStartDate()).isNotNull();
    }

    @Test
    void createSubscription_duplicateProduct_throws409() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(product.getId(), 1);
        subscriptionService.createSubscription(customer.getId(), request);

        assertThatThrownBy(() -> subscriptionService.createSubscription(customer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_DUPLICATE");
    }

    @Test
    void createSubscription_disabledProduct_throws400() {
        product.setIsAvailable(false);
        // Save via factory's product repo — need to update directly
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(product.getId(), 1);

        assertThatThrownBy(() -> subscriptionService.createSubscription(customer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void createSubscription_unknownProduct_throws400() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(UUID.randomUUID(), 1);

        assertThatThrownBy(() -> subscriptionService.createSubscription(customer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void createSubscription_onboardingIncomplete_throws403() {
        customer.setOnboardingCompleted(false);
        // customer is managed entity in transaction — change is visible
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(product.getId(), 1);

        assertThatThrownBy(() -> subscriptionService.createSubscription(customer.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ONBOARDING_INCOMPLETE");
    }

    @Test
    void pauseSubscription_activeTopaused_succeeds() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        var response = subscriptionService.pauseSubscription(customer.getId(), sub.getId());

        assertThat(response.getStatus()).isEqualTo("PAUSED");
        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.PAUSED);
        assertThat(updated.getPauseReason()).isEqualTo(Subscription.PauseReason.USER_PAUSED);
    }

    @Test
    void pauseSubscription_alreadyPaused_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        subscriptionService.pauseSubscription(customer.getId(), sub.getId());

        assertThatThrownBy(() -> subscriptionService.pauseSubscription(customer.getId(), sub.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_ALREADY_PAUSED");
    }

    @Test
    void pauseSubscription_cancelledSubscription_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        subscriptionService.cancelSubscription(customer.getId(), sub.getId());

        assertThatThrownBy(() -> subscriptionService.pauseSubscription(customer.getId(), sub.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_PAUSABLE");
    }

    @Test
    void resumeSubscription_pausedToActive_clearsPauseReason() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        subscriptionService.pauseSubscription(customer.getId(), sub.getId());

        var response = subscriptionService.resumeSubscription(customer.getId(), sub.getId());

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(updated.getPauseReason()).isNull();
    }

    @Test
    void resumeSubscription_notPaused_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        assertThatThrownBy(() -> subscriptionService.resumeSubscription(customer.getId(), sub.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_PAUSED");
    }

    @Test
    void cancelSubscription_activeToCancel_isTerminal() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        var response = subscriptionService.cancelSubscription(customer.getId(), sub.getId());

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        Subscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
    }

    @Test
    void cancelSubscription_alreadyCancelled_throws409() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        subscriptionService.cancelSubscription(customer.getId(), sub.getId());

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(customer.getId(), sub.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_ALREADY_CANCELLED");
    }

    @Test
    void listSubscriptions_filterByStatus_returnsCorrectSubset() {
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        Product product2 = factory.createProduct(3000L);
        var sub2 = factory.createActiveSubscription(customer.getId(), product2.getId(), 1);
        subscriptionService.pauseSubscription(customer.getId(), sub2.getId());

        Page<SubscriptionResponse> activePage = subscriptionService.listSubscriptions(
                customer.getId(), "ACTIVE", PageRequest.of(0, 10));
        Page<SubscriptionResponse> pausedPage = subscriptionService.listSubscriptions(
                customer.getId(), "PAUSED", PageRequest.of(0, 10));

        assertThat(activePage.getTotalElements()).isEqualTo(1);
        assertThat(pausedPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listSubscriptions_invalidStatus_throws400() {
        assertThatThrownBy(() -> subscriptionService.listSubscriptions(
                customer.getId(), "INVALID", PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATUS");
    }

    @Test
    void ownershipValidation_wrongCustomer_throws404() {
        var sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        UUID otherCustomerId = UUID.randomUUID();

        assertThatThrownBy(() -> subscriptionService.pauseSubscription(otherCustomerId, sub.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }
}
