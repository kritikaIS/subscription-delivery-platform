package com.juiceplatform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juiceplatform.entity.AdminAuditLog;
import com.juiceplatform.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes audit log entries for all admin mutations.
 * Per db-schema 1.2: audit logging happens in the same transaction as the mutation.
 * Per BR-AUD-01/02/03: all admin mutations are logged, entries are immutable.
 *
 * action_type values (from db-schema 3.15 examples):
 *   BALANCE_CREDIT, ORDER_OVERRIDE, SUBSCRIPTION_EDIT, HISTORICAL_ORDER_EDIT,
 *   HISTORICAL_DELIVERY_EDIT, MANUAL_STATUS_CORRECTION, CUSTOMER_DEACTIVATION,
 *   SCHEDULER_RERUN, PRODUCT_DISABLE, PRODUCT_PRICE_UPDATE
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Logs an admin mutation with before/after snapshots.
     *
     * @param actionType   standardised action descriptor (e.g. "BALANCE_CREDIT")
     * @param targetEntity entity type (e.g. "order", "subscription", "customer")
     * @param targetId     UUID or identifier of the affected record
     * @param oldValue     object to serialize as old_value JSONB (null for creation events)
     * @param newValue     object to serialize as new_value JSONB (null for deletion events)
     * @param actingAdmin  UUID of the admin performing the action
     * @param notes        optional admin reasoning
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(String actionType, String targetEntity, String targetId,
                    Object oldValue, Object newValue, UUID actingAdmin, String notes) {

        AdminAuditLog entry = new AdminAuditLog();
        entry.setActionType(actionType);
        entry.setTargetEntity(targetEntity);
        entry.setTargetId(targetId);
        entry.setActingAdmin(actingAdmin);
        entry.setNotes(notes);
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));

        auditLogRepository.save(entry);
    }

    /** Convenience overload without notes. */
    public void log(String actionType, String targetEntity, String targetId,
                    Object oldValue, Object newValue, UUID actingAdmin) {
        log(actionType, targetEntity, targetId, oldValue, newValue, actingAdmin, null);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log value for {}: {}", value.getClass().getSimpleName(), e.getMessage());
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
