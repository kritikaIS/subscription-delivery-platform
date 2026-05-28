package com.juiceplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks the last recharge request timestamp per customer for rate limiting.
 * Per API spec §6.3: one request per hour per customer.
 * This is infrastructure-only — no business data, no wallet mutation, no audit log.
 */
@Entity
@Table(name = "recharge_request_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeRequestLog {

    @Id
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "last_requested_at", nullable = false)
    private OffsetDateTime lastRequestedAt;
}
