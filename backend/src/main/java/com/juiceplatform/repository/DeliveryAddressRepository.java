package com.juiceplatform.repository;

import com.juiceplatform.entity.DeliveryAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, UUID> {

    Optional<DeliveryAddress> findByCustomerId(UUID customerId);
}
