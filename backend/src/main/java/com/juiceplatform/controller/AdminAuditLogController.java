package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.entity.AdminAuditLog;
import com.juiceplatform.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin-only read endpoint for audit log browsing.
 * Audit logs are immutable — no write endpoints exposed (BR-AUD-03).
 * Note: this endpoint is not defined in the API spec but is required for admin operations.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AdminAuditLog>>> listAuditLogs(
            @RequestParam(required = false) String targetEntity,
            @RequestParam(required = false) UUID actingAdmin,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @ParameterObject Pageable pageable) {

        Page<AdminAuditLog> page;

        if (targetEntity != null && !targetEntity.isBlank()) {
            page = auditLogRepository.findByTargetEntityOrderByCreatedAtDesc(targetEntity, pageable);
        } else if (actingAdmin != null) {
            page = auditLogRepository.findByActingAdminOrderByCreatedAtDesc(actingAdmin, pageable);
        } else if (from != null && to != null) {
            page = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageable);
        } else {
            page = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        PagedResponse<AdminAuditLog> data = new PagedResponse<>(page.getContent());
        PaginationMeta meta = new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }
}
