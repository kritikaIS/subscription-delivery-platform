package com.juiceplatform.repository;

import com.juiceplatform.entity.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, UUID> {
}
