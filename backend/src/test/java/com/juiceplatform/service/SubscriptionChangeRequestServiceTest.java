package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.subscription.ChangeProductRequest;
import com.juiceplatform.dto.subscription.ChangeQuantityRequest;
import com.juiceplatform.dto.subscription.ChangeRequestListEntry;
import com.juiceplatform.dto.subscription.ProductChangeResponse;
import com.juiceplatform.dto.subscription.QuantityChangeResponse;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.SubscriptionChangeRequest;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.SubscriptionChangeRequestRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class SubscriptionChangeRequestServiceTest extends AbstractIntegrationTest {

    @Autowired SubscriptionService subscriptionService;
    @Autowired OrderGenerationService orderGenerationService;
    @Autowired TestDataFactory factory;
    @Autowired SubscriptionChangeRequestRepository changeRequestRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired OrderRepository orderRepository;

    User customer;
    Product product;
    Subscription activeSubscription;

    @BeforeEach
    void setUp() {
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        product = factory.createProduct(2500L);
        factory.creditWallet(customer.getId(), 100_000L, factory.createAdmin().getId());
        activeSubscription = factory.createActiveSubscription(customer.getId(), product.getId(), 2);
    }

    // ─── Quantity Change ───────────────────────────────────────────────────────

    @Test
    void changeQuantity_happyPath_createsApprovedRequest() {
        QuantityChangeResponse response = subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(3));

        assertThat(response.getChangeRequestId()).isNotNull();
        assertThat(response.getType()).isEqualTo("QUANTITY");
        assertThat(response.getNewQuantity()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getEffectiveDate()).isNotNull();
    }

    @Test
    void changeQuantity_insertedStatusIsApproved_noPendingState() {
        subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(3));

        List<SubscriptionChangeRequest> all = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.QUANTITY,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);

        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
    }

    @Test
    void changeQuantity_supersedingOlderRequest_marksOldSuperseded() {
        subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(3));
        subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(5));

        List<SubscriptionChangeRequest> approved = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.QUANTITY,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        List<SubscriptionChangeRequest> superseded = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.QUANTITY,
                        SubscriptionChangeRequest.ChangeRequestStatus.SUPERSEDED);

        assertThat(approved).hasSize(1);
        assertThat(Integer.parseInt(approved.get(0).getNewValue())).isEqualTo(5);
        assertThat(superseded).hasSize(1);
        assertThat(Integer.parseInt(superseded.get(0).getNewValue())).isEqualTo(3);
    }

    @Test
    void changeQuantity_invalidQuantityZero_isRejectedByValidation() {
        // @Min(1) on ChangeQuantityRequest is enforced by Bean Validation at the controller layer.
        // At the service layer, a quantity of 0 would be passed through without a validation exception.
        // This test verifies the DTO constraint annotation is present and correct.
        jakarta.validation.Validator validator = jakarta.validation.Validation
                .buildDefaultValidatorFactory().getValidator();
        ChangeQuantityRequest req = new ChangeQuantityRequest(0);
        java.util.Set<jakarta.validation.ConstraintViolation<ChangeQuantityRequest>> violations =
                validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).contains("1");
    }

    @Test
    void changeQuantity_pendingStartSubscription_throws409() {
        Subscription pendingStart = new Subscription();
        pendingStart.setCustomerId(customer.getId());
        pendingStart.setProductId(factory.createProduct(3000L).getId());
        pendingStart.setQuantity(1);
        pendingStart.setStartDate(LocalDate.now().plusDays(1));
        pendingStart.setStatus(Subscription.SubscriptionStatus.PENDING_START);
        pendingStart.setCreatedBy(customer.getId());
        pendingStart = subscriptionRepository.save(pendingStart);

        final UUID subId = pendingStart.getId();
        assertThatThrownBy(() -> subscriptionService.changeQuantity(
                customer.getId(), subId, new ChangeQuantityRequest(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_MODIFIABLE");
    }

    @Test
    void changeQuantity_cancelledSubscription_throws409() {
        subscriptionService.cancelSubscription(customer.getId(), activeSubscription.getId());

        assertThatThrownBy(() -> subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_MODIFIABLE");
    }

    @Test
    void changeQuantity_pausedSubscription_succeeds() {
        subscriptionService.pauseSubscription(customer.getId(), activeSubscription.getId());

        QuantityChangeResponse response = subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(4));

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getNewQuantity()).isEqualTo(4);
    }

    @Test
    void changeQuantity_ownershipValidation_wrongCustomer_throws404() {
        UUID otherCustomer = UUID.randomUUID();
        assertThatThrownBy(() -> subscriptionService.changeQuantity(
                otherCustomer, activeSubscription.getId(), new ChangeQuantityRequest(3)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    // ─── Product Change ────────────────────────────────────────────────────────

    @Test
    void changeProduct_happyPath_createsApprovedRequest() {
        Product newProduct = factory.createProduct(3000L);

        ProductChangeResponse response = subscriptionService.changeProduct(
                customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(newProduct.getId()));

        assertThat(response.getChangeRequestId()).isNotNull();
        assertThat(response.getType()).isEqualTo("PRODUCT");
        assertThat(response.getNewProductId()).isEqualTo(newProduct.getId());
        assertThat(response.getNewProductName()).isEqualTo(newProduct.getName());
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getEffectiveDate()).isNotNull();
    }

    @Test
    void changeProduct_supersedingOlderRequest_marksOldSuperseded() {
        Product product2 = factory.createProduct(3000L);
        Product product3 = factory.createProduct(3500L);

        subscriptionService.changeProduct(customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(product2.getId()));
        subscriptionService.changeProduct(customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(product3.getId()));

        List<SubscriptionChangeRequest> approved = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.PRODUCT,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        List<SubscriptionChangeRequest> superseded = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.PRODUCT,
                        SubscriptionChangeRequest.ChangeRequestStatus.SUPERSEDED);

        assertThat(approved).hasSize(1);
        assertThat(UUID.fromString(approved.get(0).getNewValue())).isEqualTo(product3.getId());
        assertThat(superseded).hasSize(1);
    }

    @Test
    void changeProduct_quantityAndProductCoexist() {
        Product newProduct = factory.createProduct(3000L);

        subscriptionService.changeQuantity(customer.getId(), activeSubscription.getId(),
                new ChangeQuantityRequest(5));
        subscriptionService.changeProduct(customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(newProduct.getId()));

        List<SubscriptionChangeRequest> quantityApproved = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.QUANTITY,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        List<SubscriptionChangeRequest> productApproved = changeRequestRepository
                .findBySubscriptionIdAndChangeTypeAndStatus(
                        activeSubscription.getId(),
                        SubscriptionChangeRequest.ChangeRequestType.PRODUCT,
                        SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);

        // Both coexist — QUANTITY supersedes only QUANTITY, PRODUCT supersedes only PRODUCT
        assertThat(quantityApproved).hasSize(1);
        assertThat(productApproved).hasSize(1);
    }

    @Test
    void changeProduct_unavailableProduct_throws400() {
        Product unavailable = factory.createProduct(3000L);
        unavailable.setIsAvailable(false);

        assertThatThrownBy(() -> subscriptionService.changeProduct(
                customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(unavailable.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void changeProduct_nonexistentProduct_throws400() {
        assertThatThrownBy(() -> subscriptionService.changeProduct(
                customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(UUID.randomUUID())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PRODUCT_UNAVAILABLE");
    }

    @Test
    void changeProduct_duplicateSubscription_throws409() {
        // Customer already has an active subscription for product2
        Product product2 = factory.createProduct(3000L);
        factory.createActiveSubscription(customer.getId(), product2.getId(), 1);

        assertThatThrownBy(() -> subscriptionService.changeProduct(
                customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(product2.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_DUPLICATE");
    }

    @Test
    void changeProduct_pendingStartSubscription_throws409() {
        Subscription pendingStart = new Subscription();
        pendingStart.setCustomerId(customer.getId());
        pendingStart.setProductId(factory.createProduct(3000L).getId());
        pendingStart.setQuantity(1);
        pendingStart.setStartDate(LocalDate.now().plusDays(1));
        pendingStart.setStatus(Subscription.SubscriptionStatus.PENDING_START);
        pendingStart.setCreatedBy(customer.getId());
        pendingStart = subscriptionRepository.save(pendingStart);

        final UUID subId = pendingStart.getId();
        Product newProduct = factory.createProduct(4000L);
        assertThatThrownBy(() -> subscriptionService.changeProduct(
                customer.getId(), subId, new ChangeProductRequest(newProduct.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_MODIFIABLE");
    }

    @Test
    void changeProduct_cancelledSubscription_throws409() {
        subscriptionService.cancelSubscription(customer.getId(), activeSubscription.getId());
        Product newProduct = factory.createProduct(3000L);

        assertThatThrownBy(() -> subscriptionService.changeProduct(
                customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(newProduct.getId())))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("SUBSCRIPTION_NOT_MODIFIABLE");
    }

    // ─── Change Request Listing ────────────────────────────────────────────────

    @Test
    void listChangeRequests_returnsAllRequests() {
        Product newProduct = factory.createProduct(3000L);
        subscriptionService.changeQuantity(customer.getId(), activeSubscription.getId(),
                new ChangeQuantityRequest(3));
        subscriptionService.changeProduct(customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(newProduct.getId()));

        Page<ChangeRequestListEntry> page = subscriptionService.listChangeRequests(
                customer.getId(), activeSubscription.getId(), null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void listChangeRequests_filterByType_returnsCorrectSubset() {
        Product newProduct = factory.createProduct(3000L);
        subscriptionService.changeQuantity(customer.getId(), activeSubscription.getId(),
                new ChangeQuantityRequest(3));
        subscriptionService.changeProduct(customer.getId(), activeSubscription.getId(),
                new ChangeProductRequest(newProduct.getId()));

        Page<ChangeRequestListEntry> quantityPage = subscriptionService.listChangeRequests(
                customer.getId(), activeSubscription.getId(), "QUANTITY", null, PageRequest.of(0, 10));
        Page<ChangeRequestListEntry> productPage = subscriptionService.listChangeRequests(
                customer.getId(), activeSubscription.getId(), "PRODUCT", null, PageRequest.of(0, 10));

        assertThat(quantityPage.getTotalElements()).isEqualTo(1);
        assertThat(productPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listChangeRequests_ownershipValidation_wrongCustomer_throws404() {
        assertThatThrownBy(() -> subscriptionService.listChangeRequests(
                UUID.randomUUID(), activeSubscription.getId(), null, null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }

    // ─── Scheduler Integration ─────────────────────────────────────────────────

    @Test
    void scheduler_appliesQuantityChange_updatesSubscription() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Create a change request with effective_date = tomorrow
        SubscriptionChangeRequest req = new SubscriptionChangeRequest();
        req.setSubscriptionId(activeSubscription.getId());
        req.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        req.setNewValue("5");
        req.setEffectiveDate(tomorrow);
        req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        req.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        req.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(req);

        orderGenerationService.generateOrdersForDate(tomorrow);

        Subscription updated = subscriptionRepository.findById(activeSubscription.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(5);

        SubscriptionChangeRequest applied = changeRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(applied.getStatus()).isEqualTo(SubscriptionChangeRequest.ChangeRequestStatus.APPLIED);
    }

    @Test
    void scheduler_appliesProductChange_updatesSubscription() {
        Product newProduct = factory.createProduct(3500L);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        SubscriptionChangeRequest req = new SubscriptionChangeRequest();
        req.setSubscriptionId(activeSubscription.getId());
        req.setChangeType(SubscriptionChangeRequest.ChangeRequestType.PRODUCT);
        req.setNewValue(newProduct.getId().toString());
        req.setEffectiveDate(tomorrow);
        req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        req.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        req.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(req);

        orderGenerationService.generateOrdersForDate(tomorrow);

        Subscription updated = subscriptionRepository.findById(activeSubscription.getId()).orElseThrow();
        assertThat(updated.getProductId()).isEqualTo(newProduct.getId());

        SubscriptionChangeRequest applied = changeRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(applied.getStatus()).isEqualTo(SubscriptionChangeRequest.ChangeRequestStatus.APPLIED);
    }

    @Test
    void scheduler_marksRequestApplied() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        SubscriptionChangeRequest req = new SubscriptionChangeRequest();
        req.setSubscriptionId(activeSubscription.getId());
        req.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        req.setNewValue("3");
        req.setEffectiveDate(tomorrow);
        req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        req.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        req.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(req);

        orderGenerationService.generateOrdersForDate(tomorrow);

        SubscriptionChangeRequest result = changeRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(SubscriptionChangeRequest.ChangeRequestStatus.APPLIED);
    }

    @Test
    void scheduler_ignoresSupersededRequests() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Create a SUPERSEDED request
        SubscriptionChangeRequest superseded = new SubscriptionChangeRequest();
        superseded.setSubscriptionId(activeSubscription.getId());
        superseded.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        superseded.setNewValue("10");
        superseded.setEffectiveDate(tomorrow);
        superseded.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.SUPERSEDED);
        superseded.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        superseded.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(superseded);

        orderGenerationService.generateOrdersForDate(tomorrow);

        // Subscription quantity should remain unchanged (still 2)
        Subscription unchanged = subscriptionRepository.findById(activeSubscription.getId()).orElseThrow();
        assertThat(unchanged.getQuantity()).isEqualTo(2);

        // SUPERSEDED request remains SUPERSEDED
        SubscriptionChangeRequest result = changeRequestRepository.findById(superseded.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(SubscriptionChangeRequest.ChangeRequestStatus.SUPERSEDED);
    }

    @Test
    void scheduler_idempotency_doesNotApplyTwice() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        SubscriptionChangeRequest req = new SubscriptionChangeRequest();
        req.setSubscriptionId(activeSubscription.getId());
        req.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        req.setNewValue("7");
        req.setEffectiveDate(tomorrow);
        req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        req.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        req.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(req);

        // Run twice
        orderGenerationService.generateOrdersForDate(tomorrow);
        orderGenerationService.generateOrdersForDate(tomorrow);

        // Quantity should be 7, not applied twice
        Subscription updated = subscriptionRepository.findById(activeSubscription.getId()).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(7);

        // Request is APPLIED (not re-applied)
        SubscriptionChangeRequest result = changeRequestRepository.findById(req.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(SubscriptionChangeRequest.ChangeRequestStatus.APPLIED);
    }

    @Test
    void scheduler_updatesExistingScheduledOrder_whenChangeApplied() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Pre-create a SCHEDULED order for tomorrow
        Order existingOrder = new Order();
        existingOrder.setCustomerId(customer.getId());
        existingOrder.setSubscriptionId(activeSubscription.getId());
        existingOrder.setProductId(product.getId());
        existingOrder.setDeliveryLine1("42 MG Road");
        existingOrder.setDeliveryCity("Bengaluru");
        existingOrder.setDeliveryState("Karnataka");
        existingOrder.setDeliveryPincode("560001");
        existingOrder.setDeliveryDate(tomorrow);
        existingOrder.setQuantity(2);
        existingOrder.setUnitPricePaise(2500L);
        existingOrder.setTotalAmountPaise(5000L);
        existingOrder.setStatus(Order.OrderStatus.SCHEDULED);
        existingOrder.setIdempotencyKey("sub_" + activeSubscription.getId() + "_" + tomorrow);
        orderRepository.save(existingOrder);

        // Create a quantity change request
        SubscriptionChangeRequest req = new SubscriptionChangeRequest();
        req.setSubscriptionId(activeSubscription.getId());
        req.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        req.setNewValue("4");
        req.setEffectiveDate(tomorrow);
        req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        req.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        req.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(req);

        orderGenerationService.generateOrdersForDate(tomorrow);

        // The existing SCHEDULED order should be updated
        Order updatedOrder = orderRepository.findById(existingOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getQuantity()).isEqualTo(4);
        assertThat(updatedOrder.getTotalAmountPaise()).isEqualTo(4 * 2500L);
    }

    @Test
    void scheduler_doesNotMutateLockedOrder() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Pre-create a LOCKED order for tomorrow
        Order lockedOrder = new Order();
        lockedOrder.setCustomerId(customer.getId());
        lockedOrder.setSubscriptionId(activeSubscription.getId());
        lockedOrder.setProductId(product.getId());
        lockedOrder.setDeliveryLine1("42 MG Road");
        lockedOrder.setDeliveryCity("Bengaluru");
        lockedOrder.setDeliveryState("Karnataka");
        lockedOrder.setDeliveryPincode("560001");
        lockedOrder.setDeliveryDate(tomorrow);
        lockedOrder.setQuantity(2);
        lockedOrder.setUnitPricePaise(2500L);
        lockedOrder.setTotalAmountPaise(5000L);
        lockedOrder.setStatus(Order.OrderStatus.LOCKED);
        lockedOrder.setIdempotencyKey("sub_" + activeSubscription.getId() + "_" + tomorrow);
        orderRepository.save(lockedOrder);

        // Create a quantity change request
        SubscriptionChangeRequest req = new SubscriptionChangeRequest();
        req.setSubscriptionId(activeSubscription.getId());
        req.setChangeType(SubscriptionChangeRequest.ChangeRequestType.QUANTITY);
        req.setNewValue("4");
        req.setEffectiveDate(tomorrow);
        req.setStatus(SubscriptionChangeRequest.ChangeRequestStatus.APPROVED);
        req.setRequestedByType(SubscriptionChangeRequest.ChangeRequestActorType.CUSTOMER);
        req.setRequestedByUserId(customer.getId());
        changeRequestRepository.save(req);

        orderGenerationService.generateOrdersForDate(tomorrow);

        // LOCKED order must remain unchanged
        Order unchanged = orderRepository.findById(lockedOrder.getId()).orElseThrow();
        assertThat(unchanged.getQuantity()).isEqualTo(2);
        assertThat(unchanged.getStatus()).isEqualTo(Order.OrderStatus.LOCKED);
    }

    // ─── Effective Date ────────────────────────────────────────────────────────

    @Test
    void changeQuantity_effectiveDateIsNotNull() {
        QuantityChangeResponse response = subscriptionService.changeQuantity(
                customer.getId(), activeSubscription.getId(), new ChangeQuantityRequest(3));

        assertThat(response.getEffectiveDate()).isNotNull();
        // Effective date must be tomorrow or day-after-tomorrow (cutoff rule)
        LocalDate today = LocalDate.now();
        assertThat(response.getEffectiveDate()).isAfter(today);
    }
}
