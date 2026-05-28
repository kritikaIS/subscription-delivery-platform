package com.juiceplatform.service;

import com.juiceplatform.entity.DeliveryAddress;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.WalletLedger;
import com.juiceplatform.repository.DeliveryAddressRepository;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import com.juiceplatform.repository.WalletLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderGenerationService {

    private static final Logger log = LoggerFactory.getLogger(OrderGenerationService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final WalletLedgerRepository walletLedgerRepository;

    @Transactional
    public OrderGenerationResult generateOrdersForDate(LocalDate deliveryDate) {
        log.info("Starting order generation for delivery date: {}", deliveryDate);

        // Find all ACTIVE subscriptions (BR-ORD-02)
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findAllByStatus(Subscription.SubscriptionStatus.ACTIVE);

        int ordersCreated = 0;
        int duplicatesSkipped = 0;

        for (Subscription subscription : activeSubscriptions) {
            // Build idempotency key: sub_<id>_<YYYY-MM-DD> (BR-ORD-04)
            String idempotencyKey = "sub_" + subscription.getId() + "_" + deliveryDate;

            // Check for duplicate (idempotency)
            if (orderRepository.existsByIdempotencyKey(idempotencyKey)) {
                duplicatesSkipped++;
                continue;
            }

            // Load product for price snapshot (BR-ORD-06)
            Product product = productRepository.findById(subscription.getProductId()).orElse(null);
            if (product == null || !product.getIsAvailable()) {
                log.warn("Skipping subscription {} — product {} unavailable",
                        subscription.getId(), subscription.getProductId());
                continue;
            }

            // Load customer address for snapshot (BR-ONB-04)
            DeliveryAddress address = deliveryAddressRepository
                    .findByCustomerId(subscription.getCustomerId()).orElse(null);
            if (address == null) {
                log.warn("Skipping subscription {} — no delivery address for customer {}",
                        subscription.getId(), subscription.getCustomerId());
                continue;
            }

            long orderCost = product.getPricePerUnitPaise() * subscription.getQuantity();

            // Wallet balance check (BR-WAL-10 / BR-ORD-05)
            long walletBalance = walletLedgerRepository
                    .findTopByCustomerIdOrderByCreatedAtDesc(subscription.getCustomerId())
                    .map(WalletLedger::getRunningBalancePaise)
                    .orElse(0L);

            if (walletBalance < orderCost) {
                log.warn("Skipping subscription {} — insufficient wallet balance ({} < {})",
                        subscription.getId(), walletBalance, orderCost);
                // TODO: Notify customer and admin (BR-NOT-02, BR-NOT-03)
                continue;
            }

            // Create order with snapshots
            Order order = new Order();
            order.setCustomerId(subscription.getCustomerId());
            order.setSubscriptionId(subscription.getId());
            order.setProductId(product.getId());
            order.setDeliveryLine1(address.getLine1());
            order.setDeliveryLine2(address.getLine2());
            order.setDeliveryCity(address.getCity());
            order.setDeliveryState(address.getState());
            order.setDeliveryPincode(address.getPincode());
            order.setDeliveryNotes(address.getDeliveryNotes());
            order.setDeliveryDate(deliveryDate);
            order.setQuantity(subscription.getQuantity());
            order.setUnitPricePaise(product.getPricePerUnitPaise());
            order.setTotalAmountPaise(orderCost);
            order.setStatus(Order.OrderStatus.SCHEDULED);
            order.setIdempotencyKey(idempotencyKey);

            orderRepository.save(order);
            ordersCreated++;
        }

        log.info("Order generation complete for {}: {} active subscriptions processed, {} orders created, {} duplicates skipped",
                deliveryDate, activeSubscriptions.size(), ordersCreated, duplicatesSkipped);

        return new OrderGenerationResult(deliveryDate, activeSubscriptions.size(), ordersCreated, duplicatesSkipped);
    }

    public record OrderGenerationResult(
            LocalDate deliveryDate,
            int activeSubscriptionsProcessed,
            int ordersCreated,
            int duplicatesSkipped
    ) {}
}
