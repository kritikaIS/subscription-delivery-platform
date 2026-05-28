package com.juiceplatform.repository;

import com.juiceplatform.entity.WalletLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletLedgerRepository extends JpaRepository<WalletLedger, UUID> {

    /**
     * Returns the most recent ledger entry for a customer.
     * running_balance_paise on this row is the live wallet balance (BR-WAL-02).
     * Uses Spring Data Top/First to return exactly one row ordered by createdAt DESC.
     */
    Optional<WalletLedger> findTopByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Page<WalletLedger> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);
}
