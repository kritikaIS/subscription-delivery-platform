package com.juiceplatform.repository;

import com.juiceplatform.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Page<Subscription> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Subscription> findByCustomerIdAndStatus(UUID customerId, Subscription.SubscriptionStatus status, Pageable pageable);

    @Query("SELECT s FROM Subscription s WHERE s.customerId = :customerId AND s.productId = :productId AND s.status IN ('ACTIVE', 'PAUSED', 'PENDING_START')")
    Optional<Subscription> findActiveByCustomerIdAndProductId(UUID customerId, UUID productId);

    Optional<Subscription> findByIdAndCustomerId(UUID id, UUID customerId);

    List<Subscription> findAllByStatus(Subscription.SubscriptionStatus status);

    List<Subscription> findAllByStatusAndStartDateLessThanEqual(
            Subscription.SubscriptionStatus status, java.time.LocalDate date);

    /**
     * Finds all ACTIVE and PENDING_START subscriptions for a given product.
     * Used by product-disable auto-pause logic (BR-PRD-03).
     */
    List<Subscription> findAllByProductIdAndStatusIn(
            UUID productId, List<Subscription.SubscriptionStatus> statuses);
}
