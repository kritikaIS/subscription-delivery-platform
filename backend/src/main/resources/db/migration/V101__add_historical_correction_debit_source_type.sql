-- V101: Add HISTORICAL_CORRECTION_DEBIT to wallet_source_type enum.
-- Used for SKIPPED → DELIVERED historical corrections to avoid conflict
-- with the uq_wallet_ledger_order_source unique constraint on (order_id, source_type).

ALTER TYPE wallet_source_type ADD VALUE IF NOT EXISTS 'HISTORICAL_CORRECTION_DEBIT';
