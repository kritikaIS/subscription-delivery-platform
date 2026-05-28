package com.juiceplatform.repository;

import com.juiceplatform.entity.RechargeRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RechargeRequestLogRepository extends JpaRepository<RechargeRequestLog, UUID> {
}
