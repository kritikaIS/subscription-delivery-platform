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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only financial ledger. Rows are NEVER updated or deleted.
 * This is the authoritative source of financial truth (BR-WAL-02, BR-WAL-03).
 */
@Entity
@Table(name = "wallet_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletLedger {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "entry_type", nullable = false, columnDefinition = "wallet_entry_type")
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "source_type", nullable = false, columnDefinition = "wallet_source_type")
    private SourceType sourceType;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "running_balance_paise", nullable = false)
    private Long runningBalancePaise;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = OffsetDateTime.now();
    }

    public enum EntryType {
        CREDIT, DEBIT, REFUND
    }

    public enum SourceType {
        ADMIN_CREDIT, DELIVERY_DEBIT, REFUND, MANUAL_DEBIT,
        MANUAL_ADJUSTMENT, HISTORICAL_CORRECTION, HISTORICAL_CORRECTION_DEBIT, SYSTEM_ADJUSTMENT
    }
}
