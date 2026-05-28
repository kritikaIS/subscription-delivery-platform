package com.juiceplatform.repository;

import com.juiceplatform.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByIsAvailableTrueOrderBySortOrderAsc(Pageable pageable);

    Page<Product> findByIsAvailable(Boolean isAvailable, Pageable pageable);
}
