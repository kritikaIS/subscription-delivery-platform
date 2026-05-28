package com.juiceplatform.service;

import com.juiceplatform.AbstractIntegrationTest;
import com.juiceplatform.TestDataFactory;
import com.juiceplatform.dto.product.DisableProductResponse;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.User;
import com.juiceplatform.repository.AdminAuditLogRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for product disable → subscription auto-pause behavior.
 * Covers BR-PRD-03, BR-PRD-04, BR-PAU-05, BR-AUD-01, BR-NOT-01/02/03.
 */
@Transactional
class ProductDisableAutoPauseTest extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired OrderGenerationService orderGenerationService;
    @Autowired TestDataFactory factory;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;

    User admin;
    User customer;
    Product product;

    @BeforeEach
    void setUp() {
        admin = factory.createAdmin();
        customer = factory.createCustomer();
        factory.createAddress(customer.getId());
        product = factory.createProduct(2500L);
    }

    // ─── A: Disabling product pauses ACTIVE subscriptions ───────────────────

    @Test
    void disableProduct_pausesActiveSubscriptions() {
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        DisableProductResponse response = productService.disableProduct(product.getId(), admin.getId());

        assertThat(response.getAutoPausedSubscriptionCount()).isEqualTo(1);
        assertThat(response.getIsAvailable()).isFalse();

        List<Subscription> subs = subscriptionRepository
                .findAllByProductIdAndStatusIn(product.getId(),
                        List.of(Subscription.SubscriptionStatus.PAUSED));
        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).getStatus()).isEqualTo(Subscription.SubscriptionStatus.PAUSED);
        assertThat(subs.get(0).getPauseReason())
                .isEqualTo(Subscription.PauseReason.SYSTEM_PAUSED_PRODUCT_DISABLED);
    }

    @Test
    void disableProduct_pausesPendingStartSubscriptions() {
        factory.createPendingStartSubscription(customer.getId(), product.getId(), 1);

        DisableProductResponse response = productService.disableProduct(product.getId(), admin.getId());

        assertThat(response.getAutoPausedSubscriptionCount()).isEqualTo(1);

        List<Subscription> subs = subscriptionRepository
                .findAllByProductIdAndStatusIn(product.getId(),
                        List.of(Subscription.SubscriptionStatus.PAUSED));
        assertThat(subs).hasSize(1);
        assertThat(subs.get(0).getPauseReason())
                .isEqualTo(Subscription.PauseReason.SYSTEM_PAUSED_PRODUCT_DISABLED);
    }

    @Test
    void disableProduct_pausesMultipleActiveSubscriptions() {
        User customer2 = factory.createCustomer();
        factory.createAddress(customer2.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        factory.createActiveSubscription(customer2.getId(), product.getId(), 2);

        DisableProductResponse response = productService.disableProduct(product.getId(), admin.getId());

        assertThat(response.getAutoPausedSubscriptionCount()).isEqualTo(2);

        List<Subscription> paused = subscriptionRepository
                .findAllByProductIdAndStatusIn(product.getId(),
                        List.of(Subscription.SubscriptionStatus.PAUSED));
        assertThat(paused).hasSize(2);
        assertThat(paused).allMatch(s ->
                s.getPauseReason() == Subscription.PauseReason.SYSTEM_PAUSED_PRODUCT_DISABLED);
    }

    // ─── B: Already-paused subscriptions unchanged ──────────────────────────

    @Test
    void disableProduct_doesNotAffectAlreadyPausedSubscriptions() {
        Subscription sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        // Manually set to PAUSED with USER_PAUSED reason
        sub.setStatus(Subscription.SubscriptionStatus.PAUSED);
        sub.setPauseReason(Subscription.PauseReason.USER_PAUSED);
        subscriptionRepository.save(sub);

        DisableProductResponse response = productService.disableProduct(product.getId(), admin.getId());

        // Already-paused subscription is not counted and pause_reason is not changed
        assertThat(response.getAutoPausedSubscriptionCount()).isEqualTo(0);

        Subscription unchanged = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(unchanged.getPauseReason()).isEqualTo(Subscription.PauseReason.USER_PAUSED);
    }

    // ─── C: Cancelled subscriptions unchanged ───────────────────────────────

    @Test
    void disableProduct_doesNotAffectCancelledSubscriptions() {
        Subscription sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        sub.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        sub.setPauseReason(null);
        subscriptionRepository.save(sub);

        DisableProductResponse response = productService.disableProduct(product.getId(), admin.getId());

        assertThat(response.getAutoPausedSubscriptionCount()).isEqualTo(0);

        Subscription unchanged = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(Subscription.SubscriptionStatus.CANCELLED);
    }

    // ─── D: Other-product subscriptions unaffected ──────────────────────────

    @Test
    void disableProduct_doesNotAffectOtherProductSubscriptions() {
        Product otherProduct = factory.createProduct(3000L);
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        Subscription otherSub = factory.createActiveSubscription(
                customer.getId(), otherProduct.getId(), 1);

        productService.disableProduct(product.getId(), admin.getId());

        Subscription otherUnchanged = subscriptionRepository.findById(otherSub.getId()).orElseThrow();
        assertThat(otherUnchanged.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(otherUnchanged.getPauseReason()).isNull();
    }

    // ─── E: Re-disabling already-disabled product is idempotent ─────────────

    @Test
    void disableProduct_alreadyDisabled_isIdempotent() {
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        // First disable
        DisableProductResponse first = productService.disableProduct(product.getId(), admin.getId());
        assertThat(first.getAutoPausedSubscriptionCount()).isEqualTo(1);

        // Second disable — idempotent, no additional pauses
        DisableProductResponse second = productService.disableProduct(product.getId(), admin.getId());
        assertThat(second.getAutoPausedSubscriptionCount()).isEqualTo(0);
        assertThat(second.getIsAvailable()).isFalse();

        // Still only one paused subscription
        List<Subscription> paused = subscriptionRepository
                .findAllByProductIdAndStatusIn(product.getId(),
                        List.of(Subscription.SubscriptionStatus.PAUSED));
        assertThat(paused).hasSize(1);
    }

    // ─── F: Paused subscriptions do not generate future orders ──────────────

    @Test
    void disableProduct_pausedSubscriptions_doNotGenerateFutureOrders() {
        factory.creditWallet(customer.getId(), 100_000L, admin.getId());
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        productService.disableProduct(product.getId(), admin.getId());

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        OrderGenerationService.OrderGenerationResult result =
                orderGenerationService.generateOrdersForDate(tomorrow);

        // No orders created — subscription is PAUSED (BR-ORD-02 skips PAUSED)
        assertThat(result.ordersCreated()).isEqualTo(0);

        List<Order> orders = orderRepository
                .findByCustomerIdOrderByDeliveryDateDesc(customer.getId(), PageRequest.of(0, 10))
                .stream()
                .filter(o -> o.getDeliveryDate().equals(tomorrow))
                .toList();
        assertThat(orders).isEmpty();
    }

    // ─── G: Audit logs created ───────────────────────────────────────────────

    @Test
    void disableProduct_createsAuditLogForProductAndEachSubscription() {
        factory.createActiveSubscription(customer.getId(), product.getId(), 1);

        long auditCountBefore = auditLogRepository.count();

        productService.disableProduct(product.getId(), admin.getId());

        long auditCountAfter = auditLogRepository.count();
        // Expect: 1 product audit + 1 subscription audit = 2 entries
        assertThat(auditCountAfter).isEqualTo(auditCountBefore + 2);

        // Product-level audit entry
        var productAudit = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("product", PageRequest.of(0, 10))
                .getContent().stream()
                .filter(e -> e.getTargetId().equals(product.getId().toString())
                        && e.getActionType().equals("PRODUCT_DISABLE"))
                .findFirst();
        assertThat(productAudit).isPresent();
        assertThat(productAudit.get().getActingAdmin()).isEqualTo(admin.getId());

        // Subscription-level audit entry
        var subAudit = auditLogRepository
                .findByTargetEntityOrderByCreatedAtDesc("subscription", PageRequest.of(0, 10))
                .getContent().stream()
                .filter(e -> e.getActionType().equals("PRODUCT_DISABLE"))
                .findFirst();
        assertThat(subAudit).isPresent();
        assertThat(subAudit.get().getActingAdmin()).isEqualTo(admin.getId());
        assertThat(subAudit.get().getNotes()).contains(product.getId().toString());
    }

    @Test
    void disableProduct_noSubscriptions_createsOnlyProductAuditLog() {
        long auditCountBefore = auditLogRepository.count();

        productService.disableProduct(product.getId(), admin.getId());

        long auditCountAfter = auditLogRepository.count();
        // Only the product-level audit entry
        assertThat(auditCountAfter).isEqualTo(auditCountBefore + 1);
    }

    // ─── H: SCHEDULED orders remain unchanged (BR-PAU-05) ───────────────────

    @Test
    void disableProduct_existingScheduledOrders_remainUnchanged() {
        factory.creditWallet(customer.getId(), 100_000L, admin.getId());
        Subscription sub = factory.createActiveSubscription(customer.getId(), product.getId(), 1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Generate a SCHEDULED order before disabling
        orderGenerationService.generateOrdersForDate(tomorrow);

        List<Order> scheduledBefore = orderRepository
                .findBySubscriptionIdAndStatusAndDeliveryDateGreaterThanEqual(
                        sub.getId(), Order.OrderStatus.SCHEDULED, tomorrow);
        assertThat(scheduledBefore).hasSize(1);

        // Disable product — SCHEDULED orders must remain unchanged (BR-PAU-05)
        productService.disableProduct(product.getId(), admin.getId());

        List<Order> scheduledAfter = orderRepository
                .findBySubscriptionIdAndStatusAndDeliveryDateGreaterThanEqual(
                        sub.getId(), Order.OrderStatus.SCHEDULED, tomorrow);
        assertThat(scheduledAfter).hasSize(1); // still SCHEDULED, not CANCELLED
    }
}
