package com.juiceplatform.repository;

import com.juiceplatform.entity.SubscriptionChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionChangeRequestRepository extends JpaRepository<SubscriptionChangeRequest, UUID> {

    /**
     * Find all APPROVED requests of a given type for a subscription.
     * Used during supersedence logic when creating a new request.
     */
    List<SubscriptionChangeRequest> findBySubscriptionIdAndChangeTypeAndStatus(
            UUID subscriptionId,
            SubscriptionChangeRequest.ChangeRequestType changeType,
            SubscriptionChangeRequest.ChangeRequestStatus status);

    /**
     * Find all change requests for a subscription, newest first.
     * Used for the change request listing API.
     */
    Page<SubscriptionChangeRequest> findBySubscriptionIdOrderByCreatedAtDesc(
            UUID subscriptionId, Pageable pageable);

    /**
     * Find all change requests for a subscription filtered by type, newest first.
     */
    Page<SubscriptionChangeRequest> findBySubscriptionIdAndChangeTypeOrderByCreatedAtDesc(
            UUID subscriptionId,
            SubscriptionChangeRequest.ChangeRequestType changeType,
            Pageable pageable);

    /**
     * Find all change requests for a subscription filtered by status, newest first.
     */
    Page<SubscriptionChangeRequest> findBySubscriptionIdAndStatusOrderByCreatedAtDesc(
            UUID subscriptionId,
            SubscriptionChangeRequest.ChangeRequestStatus status,
            Pageable pageable);

    /**
     * Find all change requests for a subscription filtered by type and status, newest first.
     */
    Page<SubscriptionChangeRequest> findBySubscriptionIdAndChangeTypeAndStatusOrderByCreatedAtDesc(
            UUID subscriptionId,
            SubscriptionChangeRequest.ChangeRequestType changeType,
            SubscriptionChangeRequest.ChangeRequestStatus status,
            Pageable pageable);

    /**
     * Find all APPROVED requests with effective_date <= targetDate for a subscription.
     * Used by OrderGenerationService to apply pending changes.
     */
    List<SubscriptionChangeRequest> findBySubscriptionIdAndStatusAndEffectiveDateLessThanEqual(
            UUID subscriptionId,
            SubscriptionChangeRequest.ChangeRequestStatus status,
            LocalDate targetDate);

    /**
     * Find APPROVED requests of a given type with effective_date <= targetDate.
     * Used by OrderGenerationService to apply pending changes per type.
     */
    List<SubscriptionChangeRequest> findBySubscriptionIdAndChangeTypeAndStatusAndEffectiveDateLessThanEqual(
            UUID subscriptionId,
            SubscriptionChangeRequest.ChangeRequestType changeType,
            SubscriptionChangeRequest.ChangeRequestStatus status,
            LocalDate targetDate);
}
