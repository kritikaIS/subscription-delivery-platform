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
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "delivery_line1", nullable = false, length = 255)
    private String deliveryLine1;

    @Column(name = "delivery_line2", length = 255)
    private String deliveryLine2;

    @Column(name = "delivery_city", nullable = false, length = 100)
    private String deliveryCity;

    @Column(name = "delivery_state", nullable = false, length = 100)
    private String deliveryState;

    @Column(name = "delivery_pincode", nullable = false, length = 10)
    private String deliveryPincode;

    @Column(name = "delivery_notes", columnDefinition = "TEXT")
    private String deliveryNotes;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    @Column(name = "total_amount_paise", nullable = false)
    private Long totalAmountPaise;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "order_status")
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "skip_reason", columnDefinition = "skip_reason")
    private SkipReason skipReason;

    @Column(name = "cancellation_comment", columnDefinition = "TEXT")
    private String cancellationComment;

    @Column(name = "cancellation_commented_by")
    private UUID cancellationCommentedBy;

    @Column(name = "cancellation_commented_at")
    private OffsetDateTime cancellationCommentedAt;

    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = OffsetDateTime.now();
        if (this.status == null) {
            this.status = OrderStatus.SCHEDULED;
        }
    }

    public enum OrderStatus {
        SCHEDULED, LOCKED, DELIVERED, SKIPPED, CANCELLED
    }

    public enum SkipReason {
        CUSTOMER_UNAVAILABLE, PRODUCT_UNAVAILABLE, DAMAGED, OTHER
    }
}
