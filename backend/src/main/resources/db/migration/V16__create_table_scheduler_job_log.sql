-- V16: Create scheduler_job_log table
-- Tracks all scheduler job executions. Enforces idempotency per (job_name, job_date).

CREATE TABLE scheduler_job_log (
    id             UUID                  NOT NULL DEFAULT gen_random_uuid(),
    job_name       VARCHAR(100)          NOT NULL,
    job_date       DATE                  NOT NULL,
    status         scheduler_job_status  NOT NULL DEFAULT 'RUNNING',
    started_at     TIMESTAMPTZ           NOT NULL DEFAULT NOW(),
    finished_at    TIMESTAMPTZ           NULL,
    rows_processed INTEGER               NULL,
    error_message  TEXT                  NULL,
    created_at     TIMESTAMPTZ           NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_scheduler_job_log PRIMARY KEY (id),
    CONSTRAINT uq_scheduler_job_log_name_date UNIQUE (job_name, job_date),
    CONSTRAINT chk_scheduler_job_log_finished
        CHECK (
            (status IN ('COMPLETED', 'FAILED') AND finished_at IS NOT NULL) OR
            (status = 'RUNNING' AND finished_at IS NULL)
        )
);
