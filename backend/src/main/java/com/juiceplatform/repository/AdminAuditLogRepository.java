package com.juiceplatform.repository;

import com.juiceplatform.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    Page<AdminAuditLog> findByTargetEntityOrderByCreatedAtDesc(String targetEntity, Pageable pageable);

    Page<AdminAuditLog> findByActingAdminOrderByCreatedAtDesc(UUID actingAdmin, Pageable pageable);

    Page<AdminAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
