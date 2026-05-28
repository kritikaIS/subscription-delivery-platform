package com.juiceplatform.repository;

import com.juiceplatform.entity.DeliveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<DeliveryRecord> findByOrderId(UUID orderId);
}
