package com.juiceplatform.repository;

import com.juiceplatform.entity.WalletLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletLedgerRepository extends JpaRepository<WalletLedger, UUID> {

    /**
     * Returns the most recent ledger entry for a customer.
     * running_balance_paise on this row is the live wallet balance (BR-WAL-02).
     * Ordered by created_at DESC, id DESC to handle same-timestamp entries.
     */
    @Query("SELECT w FROM WalletLedger w WHERE w.customerId = :customerId ORDER BY w.createdAt DESC, w.id DESC")
    Optional<WalletLedger> findLatestByCustomerId(UUID customerId);

    Page<WalletLedger> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);
}
