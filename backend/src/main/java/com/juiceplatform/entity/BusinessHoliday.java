package com.juiceplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin-managed holiday table.
 * Supports hard deletion — the only entity in MVP that does (BR-GEN-01).
 * Schema matches db-schema section 3.7 exactly.
 */
@Entity
@Table(name = "business_holidays")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BusinessHoliday {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = OffsetDateTime.now();
    }
}
