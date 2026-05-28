package com.juiceplatform.repository;

import com.juiceplatform.entity.DeliverySheetSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliverySheetSnapshotRepository extends JpaRepository<DeliverySheetSnapshot, UUID> {

    Optional<DeliverySheetSnapshot> findByDeliveryDate(LocalDate deliveryDate);

    boolean existsByDeliveryDate(LocalDate deliveryDate);
}
