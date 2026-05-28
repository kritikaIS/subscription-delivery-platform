package com.juiceplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Best-effort, non-blocking notification service.
 * Per BR-NOT-01: email-only, failures are logged and never affect business operations.
 * Notification dispatch must never participate in financial database transactions.
 * Business transactions commit first; notifications are sent after commit.
 *
 * MVP: notifications are logged only (no SMTP configured).
 * Production: replace log statements with actual email dispatch.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Notifies admin that a customer has requested a wallet recharge.
     * BR-NOT-02: recharge-request only triggers admin notification workflow.
     * No audit log, no wallet ledger entry.
     */
    public void notifyAdminWalletRechargeRequested(UUID customerId, String customerName,
                                                    String notes, long currentBalancePaise) {
        try {
            log.info("[NOTIFICATION] Admin alert: Customer {} ({}) requested wallet recharge. " +
                     "Current balance: {} paise. Notes: {}",
                     customerId, customerName, currentBalancePaise, notes);
            // TODO: Send email to admin when SMTP is configured
        } catch (Exception e) {
            // BR-NOT-01: failures are logged and never affect business operations
            log.warn("[NOTIFICATION] Failed to notify admin of recharge request for customer {}: {}",
                     customerId, e.getMessage());
        }
    }

    /**
     * Notifies customer and admin of low wallet balance.
     * BR-NOT-02: low balance warning (balance < ₹200).
     * BR-NOT-03: admin also notified.
     */
    public void notifyLowBalance(UUID customerId, String customerName,
                                  long balancePaise, long thresholdPaise) {
        try {
            log.info("[NOTIFICATION] Low balance warning: Customer {} ({}) balance {} paise < threshold {} paise",
                     customerId, customerName, balancePaise, thresholdPaise);
            // TODO: Send email to customer and admin when SMTP is configured
        } catch (Exception e) {
            log.warn("[NOTIFICATION] Failed to send low balance warning for customer {}: {}",
                     customerId, e.getMessage());
        }
    }

    /**
     * Notifies customer and admin that order generation was blocked due to insufficient balance.
     * BR-NOT-02, BR-NOT-03, BR-ORD-05.
     */
    public void notifyOrderGenerationBlocked(UUID customerId, String customerName,
                                              long balancePaise, long requiredPaise) {
        try {
            log.info("[NOTIFICATION] Order generation blocked: Customer {} ({}) balance {} paise < required {} paise",
                     customerId, customerName, balancePaise, requiredPaise);
            // TODO: Send email to customer and admin when SMTP is configured
        } catch (Exception e) {
            log.warn("[NOTIFICATION] Failed to notify order generation blocked for customer {}: {}",
                     customerId, e.getMessage());
        }
    }

    /**
     * Notifies customer that their wallet was credited.
     * BR-NOT-02: wallet credited trigger.
     */
    public void notifyWalletCredited(UUID customerId, String customerName,
                                      long amountPaise, long newBalancePaise) {
        try {
            log.info("[NOTIFICATION] Wallet credited: Customer {} ({}) received {} paise. New balance: {} paise",
                     customerId, customerName, amountPaise, newBalancePaise);
            // TODO: Send email to customer when SMTP is configured
        } catch (Exception e) {
            log.warn("[NOTIFICATION] Failed to notify wallet credit for customer {}: {}",
                     customerId, e.getMessage());
        }
    }

    /**
     * Notifies admin of a scheduler job failure.
     * BR-NOT-03: scheduler job failure trigger.
     */
    public void notifySchedulerJobFailure(String jobName, LocalDate jobDate, String errorMessage) {
        try {
            log.error("[NOTIFICATION] Scheduler job failure: {} for date {} — {}",
                      jobName, jobDate, errorMessage);
            // TODO: Send email to admin when SMTP is configured
        } catch (Exception e) {
            log.warn("[NOTIFICATION] Failed to notify scheduler job failure for {}: {}",
                     jobName, e.getMessage());
        }
    }
}
