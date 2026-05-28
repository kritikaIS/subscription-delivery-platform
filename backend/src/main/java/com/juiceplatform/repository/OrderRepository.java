package com.juiceplatform.repository;

import com.juiceplatform.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerIdOrderByDeliveryDateDesc(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatusOrderByDeliveryDateDesc(UUID customerId, Order.OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Order> findByDeliveryDateAndStatus(LocalDate deliveryDate, Order.OrderStatus status);
}
