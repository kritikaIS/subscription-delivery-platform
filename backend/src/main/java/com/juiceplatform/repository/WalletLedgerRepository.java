package com.juiceplatform.repository;

import com.juiceplatform.entity.WalletLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletLedgerRepository extends JpaRepository<WalletLedger, UUID> {

    /**
     * Returns the most recent ledger entry for a customer.
     * running_balance_paise on this row is the live wallet balance (BR-WAL-02).
     * Uses Spring Data Top/First to return exactly one row ordered by createdAt DESC.
     *
     * Use this for READ-ONLY balance checks (e.g. wallet summary, order generation).
     */
    Optional<WalletLedger> findTopByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Returns the most recent ledger entry for a customer with a pessimistic write lock
     * (SELECT ... FOR UPDATE). Must be called inside an active @Transactional context.
     *
     * Use this before inserting any new ledger entry that depends on the current balance
     * (admin credit, delivery debit, historical correction refund/debit).
     * This prevents concurrent transactions from reading the same running_balance_paise
     * and producing an incorrect double-mutation (db-schema §6.1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletLedger w WHERE w.customerId = :customerId " +
           "ORDER BY w.createdAt DESC, w.id DESC LIMIT 1")
    Optional<WalletLedger> findTopByCustomerIdForUpdate(@Param("customerId") UUID customerId);

    Page<WalletLedger> findByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);
}
