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
@Table(name = "delivery_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "delivery_window", nullable = false, length = 50)
    private String deliveryWindow;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "delivery_record_status")
    private DeliveryRecordStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "skip_reason", columnDefinition = "skip_reason")
    private Order.SkipReason skipReason;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "photo_proof_url", columnDefinition = "TEXT")
    private String photoProofUrl;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.deliveryWindow == null) {
            this.deliveryWindow = "Morning";
        }
        if (this.status == null) {
            this.status = DeliveryRecordStatus.PENDING;
        }
    }

    public enum DeliveryRecordStatus {
        PENDING, DELIVERED, SKIPPED, CANCELLED
    }
}
