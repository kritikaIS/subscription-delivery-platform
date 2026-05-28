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

/**
 * Nightly delivery sheet snapshot.
 * One snapshot per delivery date. Replaced on rerun (not append-only).
 * Schema matches db-schema section 3.13 exactly.
 */
@Entity
@Table(name = "delivery_sheet_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliverySheetSnapshot {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_date", nullable = false, unique = true)
    private LocalDate deliveryDate;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "generated_by_source", nullable = false, columnDefinition = "delivery_sheet_source")
    private GeneratedBySource generatedBySource;

    @Column(name = "generated_by_user_id")
    private UUID generatedByUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.generatedAt == null) {
            this.generatedAt = OffsetDateTime.now();
        }
    }

    public enum GeneratedBySource {
        SCHEDULER, ADMIN_RERUN
    }
}
