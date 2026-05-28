package com.juiceplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscription_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionChangeRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "change_type", nullable = false, columnDefinition = "change_request_type")
    private ChangeRequestType changeType;

    /**
     * Plain string value.
     * QUANTITY: integer as string, e.g. "3"
     * PRODUCT:  product UUID as string, e.g. "prod-uuid-2"
     */
    @Column(name = "new_value", nullable = false)
    private String newValue;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "change_request_status")
    private ChangeRequestStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "requested_by_type", nullable = false, columnDefinition = "change_request_actor_type")
    private ChangeRequestActorType requestedByType;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.status == null) {
            this.status = ChangeRequestStatus.APPROVED;
        }
    }

    public enum ChangeRequestType {
        QUANTITY, PRODUCT
    }

    public enum ChangeRequestStatus {
        APPROVED, APPLIED, SUPERSEDED
    }

    public enum ChangeRequestActorType {
        CUSTOMER, ADMIN
    }
}
