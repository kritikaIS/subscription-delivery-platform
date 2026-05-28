# Juice Delivery Platform — Business Rules

All rules are numbered for direct reference in code reviews, prompts, and test cases.
Rules are grouped by domain. Where a rule conflicts with another, the higher-numbered rule in the same section takes precedence unless explicitly stated otherwise.

---

## 1. Authentication & Session

**BR-AUTH-01**
Customers authenticate using Google Sign-In only. No OTP, no password, no email/password login.

**BR-AUTH-02**
The backend verifies the Google ID token server-side on every login. Client-side token validation is not trusted.

**BR-AUTH-03**
On successful login, the backend issues a JWT access token and a refresh token. Both are scoped to a single session.

**BR-AUTH-04**
Only one active session is allowed per customer at any time. When a new login occurs, the previous refresh token is immediately revoked. Revocation is enforced at token refresh time — the old access token may remain technically valid until expiry, but the old refresh token will be rejected.

**BR-AUTH-05**
Admin authentication uses phone number + password only.

**BR-AUTH-06**
Only one admin account exists. No admin roles or permission tiers are required.

**BR-AUTH-07**
Backend server time is the authoritative source for all token expiry and cutoff calculations. Client device time is never trusted.

---

## 2. Onboarding

**BR-ONB-01**
A customer account is created at first Google Sign-In. Onboarding is considered complete only when both a phone number and a delivery address have been provided.

**BR-ONB-02**
Customers with incomplete onboarding cannot create subscriptions and cannot access any customer business APIs (orders, wallet, delivery history). They may only access onboarding endpoints.

**BR-ONB-03**
Address changes bypass change requests entirely and apply immediately at the customer profile level. Future order generation always uses the latest customer address.

**BR-ONB-04**
Orders store an immutable address snapshot at order generation time. Future customer profile address changes do not modify existing orders.

---

## 3. Cutoff Rule

**BR-CUT-01**
The cutoff time is **22:00:00 IST** (10:00 PM Indian Standard Time) every day. This is a hard wall based strictly on server clock time — job execution timing is irrelevant.

**BR-CUT-02**
Any customer action submitted at or after 22:00:00 IST is treated as after-cutoff.

**BR-CUT-03 — Before cutoff (before 22:00:00 IST)**
The effective date for any subscription creation, change request, pause, or resume is **tomorrow**.

**BR-CUT-04 — After cutoff (at or after 22:00:00 IST)**
The effective date for any subscription creation, change request, pause, or resume is **day-after-tomorrow**.

**BR-CUT-05**
The cutoff rule applies to: subscription creation, quantity change requests, product change requests, pause requests, and resume requests. It does NOT apply to address changes (BR-ONB-03).

---

## 4. Subscriptions

**BR-SUB-01**
A customer may have multiple concurrent active subscriptions, each for a different product. Duplicate subscriptions for the same product are forbidden — enforced at the service layer.

**BR-SUB-02**
Each subscription is fully independent. One subscription being PAUSED does not affect the state of another subscription for the same customer.

**BR-SUB-03**
Subscription states are: `PENDING_START`, `ACTIVE`, `PAUSED`, `CANCELLED`.

**BR-SUB-04**
`CANCELLED` is a terminal state. A cancelled subscription can never be reactivated. The customer may create a new subscription for the same product after cancellation — this is treated as a fresh subscription.

**BR-SUB-05**
`PENDING_START` subscriptions transition to `ACTIVE` during `OrderGenerationJob` once their effective start date becomes eligible for order generation. This transition is scheduler-driven only — never event-driven.

**BR-SUB-06**
Subscriptions have no fixed end date. They recur indefinitely until cancelled or paused.

**BR-SUB-07**
Change requests (QUANTITY or PRODUCT type) are allowed only for `ACTIVE` or `PAUSED` subscriptions. They are forbidden for `PENDING_START` and `CANCELLED` subscriptions.

**BR-SUB-08**
When a new change request of the same type is submitted for a subscription, any existing `APPROVED` request of that type is immediately marked `SUPERSEDED`. Only the newest request of each type is ever `APPLIED`.

A subscription may simultaneously have:
- one APPROVED QUANTITY request
- one APPROVED PRODUCT request

Submitting a new request supersedes only existing APPROVED requests of the same type.

**BR-SUB-09**
Change requests are inserted directly in `APPROVED` state. No `PENDING` state exists.

**BR-SUB-10**
The scheduler applies `APPROVED` change requests on their effective date during `OrderGenerationJob`, then marks them `APPLIED`.

Change request creation, supersedence, and application must be transactionally serialized per subscription to prevent concurrent APPROVED/APPLIED/SUPERSEDED race conditions.

**BR-SUB-11**
If an `APPROVED` subscription change becomes effective for an already-generated `SCHEDULED` order for the target operational delivery date, that `SCHEDULED` order must immediately be updated before lock time.

`LOCKED` orders are never automatically modified by subscription change requests.

---

## 5. Pause & Resume

**BR-PAU-01**
When a subscription is paused:
- Existing `LOCKED` orders remain unchanged and are fulfilled as normal.
- Future `SCHEDULED` orders are set to `CANCELLED` (cancellation reason: `SUBSCRIPTION_PAUSED`).
- No new orders are generated for the subscription while it is paused.

**BR-PAU-02**
The effective date of a pause follows the cutoff rule (BR-CUT-03 / BR-CUT-04).

**BR-PAU-03**
When a subscription is resumed:
- Delivery resumes from the next eligible delivery date after the effective resume date.
- Orders that were cancelled during the pause window are **never regenerated**. No historical backfilling occurs.

**BR-PAU-04**
The effective date of a resume follows the cutoff rule (BR-CUT-03 / BR-CUT-04).

**BR-PAU-05**
Pause behavior depends on pause reason.

`USER_PAUSED` subscriptions cancel future `SCHEDULED` orders.

`SYSTEM_PAUSED_PRODUCT_DISABLED` subscriptions — already-generated `SCHEDULED` orders remain unchanged and fulfillable, but future order generation is prevented.

`CUSTOMER_DEACTIVATED` subscriptions — already-generated `SCHEDULED` orders remain unchanged and fulfillable, but future order generation is prevented.

---

## 6. Cancellation

**BR-CAN-01**
When a subscription is cancelled:
- Existing `LOCKED` orders remain unchanged and are fulfilled as normal.
- Future `SCHEDULED` orders are set to `CANCELLED` (cancellation reason: `SUBSCRIPTION_CANCELLED`).
- No future orders are ever generated for the subscription.

**BR-CAN-02**
Cancellation is subject to the cutoff rule (BR-CUT-03 / BR-CUT-04) for effective date calculation.

**BR-CAN-03**
Admin may still override locked orders after a subscription is cancelled (e.g. to mark them SKIPPED if delivery cannot proceed).

---

## 7. Products

**BR-PRD-01**
Products are never hard deleted. They may only be disabled (`is_available = false`) or re-enabled.

**BR-PRD-02**
A disabled product is hidden from the customer app. No new subscriptions can be created for it.

**BR-PRD-03**
When a product is disabled:
- All `ACTIVE` and `PENDING_START` subscriptions for that product are automatically transitioned to `PAUSED`.
- Both the admin and the affected customers are notified.
- Existing `LOCKED` and `SCHEDULED` orders for that product remain unchanged and are still fulfillable. Disabling a product only prevents future order generation.
- No new orders are generated for the product while disabled.

**BR-PRD-04**
When a product is re-enabled:
- Subscriptions that were auto-paused due to product disablement are **not** automatically resumed.
- Admin or customer must manually resume each affected subscription.
- Auto-resume on product re-enable is explicitly not supported.

---

## 8. Order Generation

**BR-ORD-01**
`OrderGenerationJob` runs daily at **22:05 IST** and generates orders for the next operational delivery date.

**BR-ORD-02**
Order generation evaluates:
- `ACTIVE` subscriptions
- `PENDING_START` subscriptions whose effective start date matches the target operational delivery date.

Generation is skipped for:
- `PAUSED` subscriptions
- `CANCELLED` subscriptions

**BR-ORD-03**
Order generation skips holidays. No order is generated for a delivery date that falls on a configured holiday.

**BR-ORD-04**
Idempotency is enforced via a unique `idempotency_key` (format: `sub_<id>_<YYYY-MM-DD>`). Re-running the job will not create duplicate orders.

**BR-ORD-05 — Insufficient balance:**
If the customer's wallet balance is less than the cost of the upcoming order during `OrderGenerationJob`:
- No order row is created.
- The customer and admin are both notified.
- The subscription remains `ACTIVE`.
- Recovery: admin credits the wallet, then manually reruns `OrderGenerationJob`. The rerun safely generates the previously blocked order.

OrderGenerationJob reruns must always reevaluate all eligible subscriptions for the target operational delivery date.

Recovery from insufficient balance does not depend on a previously created blocked-order record or placeholder order row.

**BR-ORD-06**
Order unit price is set at the time of order generation using the product's current price. Price changes after order generation do not affect existing orders.

**BR-ORD-07**
`PENDING_START` subscriptions whose effective start date matches the target order generation date are transitioned to `ACTIVE` before order generation proceeds for that subscription.

**BR-ORD-08**
If a subscription reaches `PENDING_START` activation date on a business holiday, activation is deferred until the next operational delivery date.

**BR-ORD-09**
`APPROVED` change requests whose effective date falls on a holiday are deferred until the next operational delivery date.

---

## 9. Order States & Locking

**BR-LCK-01**
`OrderFreezeJob` runs daily at **22:00 IST** and transitions eligible `SCHEDULED` orders to `LOCKED` state.

**BR-LCK-02**
Once `LOCKED`, customers can no longer modify the order. Admin may still override a locked order.

**BR-LCK-03**
Order states and their semantics:

| State | Meaning |
|---|---|
| `SCHEDULED` | Generated, awaiting delivery day. Customer-modifiable subject to cutoff. |
| `LOCKED` | Frozen at 22:00 cutoff. Customer cannot modify. Admin can override. |
| `DELIVERED` | Delivery confirmed. Wallet deducted in the same DB transaction. |
| `SKIPPED` | Delivery-day operational skip (customer unavailable, damaged item, operational issue). No wallet deduction. |
| `CANCELLED` | Pre-delivery business invalidation (subscription paused/cancelled, product disabled). No wallet deduction. Orders cancelled before LOCKED create no delivery record.|

**BR-LCK-04**
Orders cancelled before reaching `LOCKED` state never create delivery records.

However, if a previously `LOCKED` order undergoes historical correction to `CANCELLED`, the existing `delivery_record` is retained and its status transitions to `CANCELLED`.

**BR-LCK-05**
Admin can manually mark any `LOCKED` order as `DELIVERED` or `SKIPPED` outside the standard delivery confirmation workflow.

**BR-LCK-06**
A `delivery_record` row is created during `OrderFreezeJob` when an order transitions from `SCHEDULED` → `LOCKED`.

The record is initialized with:
- `status = PENDING`
- `delivery_date`
- `delivery_window`

During delivery operations, the existing `delivery_record` row is later updated to:
- `DELIVERED`
- or `SKIPPED`

`CANCELLED` orders never create delivery records.

Repeated delivery confirmation requests for an already `DELIVERED` order are treated as idempotent success.

The system:
- returns the existing successful delivery result
- performs no additional wallet deduction
- creates no additional ledger entry
- returns HTTP 200

---

## 10. Delivery

**BR-DEL-01**
Delivery outcomes are binary: `DELIVERED` or `SKIPPED`. No partial delivery, no `FAILED` state.

**BR-DEL-02**
Delivered quantity always matches the subscription quantity exactly. No actual delivered quantity is recorded separately.

**BR-DEL-03**
Each order is confirmed individually by the admin. There is no bulk confirmation action.

**BR-DEL-04**
When an order is marked `DELIVERED`:
- Wallet balance is deducted by `total_amount_paise` in the same DB transaction as the status update.
- A `DEBIT` ledger entry is inserted in the same transaction.
- The transaction must be atomic — if either operation fails, both are rolled back.

**BR-DEL-05**
When an order is marked `SKIPPED`, no wallet deduction occurs and no ledger entry is created.

**BR-DEL-06**
Delivery confirmation emails are manually triggered by the admin after confirming delivery. They are never sent automatically.

---

## 11. Wallet & Ledger

**BR-WAL-01**
The wallet is a prepaid system.

Negative balances are not allowed during normal operational flows.

Negative balances are permitted only for admin-forced historical correction operations.

**BR-WAL-02**
Wallet balance is never stored as a mutable column on the customer record. It is tracked via the append-only `wallet_ledger` table. For performance, each ledger row stores a `running_balance_paise` column computed at insert time. The live balance is read from the latest ledger row. The full ledger history remains the financial source of truth and can recompute balance if needed.

**BR-WAL-03**
All ledger entries are immutable. No ledger row is ever updated or deleted.

**BR-WAL-04**
Every financial event inserts a new ledger row. The ledger is append-only.

**BR-WAL-05**
All monetary values are stored as `BIGINT` in paise (1 INR = 100 paise). No floating point arithmetic is used anywhere in financial calculations.

**BR-WAL-06**
Ledger `entry_type` and `source_type` are two separate fields with separate enums:
- `entry_type`: broad financial direction — `CREDIT`, `DEBIT`, `REFUND`, `ADJUSTMENT`
- `source_type`: operational origin — `ADMIN_CREDIT`, `DELIVERY_DEBIT`, `REFUND`, `MANUAL_DEBIT`, `MANUAL_ADJUSTMENT`, `HISTORICAL_CORRECTION`, `SYSTEM_ADJUSTMENT`

Wallet balance computation rules:
- CREDIT, REFUND, and ADJUSTMENT entries increase balance
- DEBIT entries decrease balance

**BR-WAL-07**
Minimum wallet top-up is ₹1 (100 paise). There is no maximum. Admin may credit any amount ≥ 100 paise.

The ₹1 (100 paise) minimum applies only to ADMIN_CREDIT operations representing external customer payments.

System-generated REFUND entries and internal ADJUSTMENT operations may be lower than ₹1 when required for reconciliation or historical correction flows.

**BR-WAL-08**
There is no payment gateway, auto-recharge, or online payment processing. All wallet credits are applied manually by the admin after the customer pays externally.

**BR-WAL-09 — Low balance warning:**
If wallet balance < ₹200 (20,000 paise) during `OrderGenerationJob`, the customer and admin are notified. This is informational only — delivery is not blocked.

**BR-WAL-10 — Order generation block:**
If wallet balance < upcoming order cost during `OrderGenerationJob`, no order row is created. Customer and admin are notified. Subscription remains `ACTIVE`. (See BR-ORD-05 for recovery.)

**BR-WAL-11**
BR-WAL-09 and BR-WAL-10 are independent checks. Both may trigger for the same customer on the same night. BR-WAL-09 does not block orders; BR-WAL-10 does.

**BR-WAL-12**
Admin may operationally set a customer's wallet balance directly. This is implemented internally by inserting a `SYSTEM_ADJUSTMENT` ledger entry. Existing ledger rows are never modified or deleted.

Admin set-balance operations compute a delta against the current wallet balance.

Rules:
- amount_paise stores the absolute delta amount
- `entry_type` = `CREDIT` when new balance > current balance
- `entry_type` = `DEBIT` when new balance < current balance
- `source_type` = `SYSTEM_ADJUSTMENT`
- `running_balance_paise` stores the resulting final balance

**BR-WAL-13**
Customers with no `wallet_ledger` entries have a computed wallet balance of 0 paise.

---

## 12. Historical Corrections

**BR-HIS-01**
Admins may edit historical orders and historical delivery records. All such edits are audit logged with before/after snapshots.

When a LOCKED order is historically corrected to CANCELLED:
- the associated delivery_record row must NOT be deleted
- delivery_record.status transitions to CANCELLED
- delivery sheet generation and delivery workflows must ignore CANCELLED delivery records

**BR-HIS-02**
Automatic refunds are supported only for `DELIVERED` → `SKIPPED` corrections where `isSystemError=true`. When this condition is met, the wallet deduction is automatically reversed via a `REFUND` ledger entry inserted in the same transaction.

**BR-HIS-03**
A `SKIPPED` → `DELIVERED` historical correction is treated as a standard delivery confirmation and must immediately create a `DEBIT` ledger entry with `source_type = DELIVERY_DEBIT`. This debit is inserted in the same transaction as the status update.

If the resulting balance becomes negative during a historical correction debit, the operation is still permitted and the negative balance must be recorded.

Historical correction debit operations are exempt from normal insufficient-balance rejection rules.

SKIPPED → DELIVERED corrections must proceed even if the resulting balance becomes negative.

**BR-HIS-04**
All other financial corrections (i.e. where `isSystemError=false` or for any correction type other than `DELIVERED` → `SKIPPED`) require separate manual ledger actions. No automatic balance adjustment occurs.

**BR-HIS-05**
Any admin modification of quantity or product on an existing order must immediately recalculate:
- `unit_price_paise`
- `total_amount_paise`

before transaction commit.

If only quantity is modified during an admin override, recalculation must use the existing historical unit_price_paise already stored on the order.

If product is changed, recalculation uses the current product price at the time of the admin override.

Admin subscription overrides apply immediately and bypass normal cutoff/effective-date scheduling.

If an admin directly pauses or cancels a subscription:
- future `SCHEDULED` orders are immediately transitioned to `CANCELLED`
- no pauseEffectiveDate or future effective date is computed
- `LOCKED` orders remain unchanged

**BR-HIS-06**
Ledger entries are always immutable regardless of historical corrections. Balance corrections are always handled by inserting new ledger entries, never by editing existing ones.

---

## 13. Holidays

**BR-HOL-01**
Holidays are managed by the admin manually via the dashboard, one at a time. No bulk import is supported.

**BR-HOL-02**
No order is generated for a delivery date that falls on a configured holiday (BR-ORD-03).

**BR-HOL-03**
Holidays must be configured before `OrderGenerationJob` runs for that date. A holiday added after order generation has already run for a date has no effect on existing orders — admin must manually cancel affected orders if needed.

---

## 14. Delivery Slot

**BR-SLT-01**
A single system-defined delivery slot exists for all subscriptions.

**BR-SLT-02**
The slot exists only for operational grouping and delivery sheet generation.

**BR-SLT-03**
Customers cannot view, select, or modify slot information.

---

## 15. Scheduler

**BR-SCH-01**
Nightly job schedule (IST):

| Time | Job |
|---|---|
| 22:05 | `OrderGenerationJob` — generates orders for the next operational delivery date; transitions PENDING_START → ACTIVE; applies APPROVED change requests |
| 22:00 | `OrderFreezeJob` — transitions eligible SCHEDULED orders to LOCKED state |
| 22:10 | `DeliverySheetGenerationJob` — generates nightly delivery sheet snapshot after successful order freezing |

**BR-SCH-02**
All jobs are idempotent. Rerunning any job produces the same result as running it once.

Scheduler job idempotency prevents concurrent execution, not manual or recovery reruns.

If a scheduler_job_log entry already exists for the same (job_name, job_date):
- status = RUNNING → rerun must be rejected
- status = COMPLETED or FAILED → rerun is allowed

Order-level idempotency remains the primary protection against duplicate order creation.

**BR-SCH-03**
All job executions are tracked in the database (job name, run time, status, error if any).

**BR-SCH-04**
If a job was missed (e.g. server downtime), it is automatically rerun on application startup.

Automatic or manual reruns must preserve the original intended operational target date of the missed execution.

On application startup, the scheduler checks the previous 3 calendar days for missed scheduler jobs.

Missed jobs are rerun:
1. in chronological order
2. and in job-sequence order:
   - OrderGenerationJob
   - OrderFreezeJob
   - DeliverySheetGenerationJob

**BR-SCH-05**
Admin may manually trigger a rerun of any job from the dashboard.

**BR-SCH-06**
Job failures are logged and the admin is notified. Failures never silently pass.

---

## 16. Notifications

**BR-NOT-01**
All notifications are email-only, best-effort, and non-blocking. A notification failure is logged internally and never affects any business operation.

Notification dispatch must never participate in financial database transactions.

Business transactions commit first. Notification sending occurs asynchronously or only after successful transaction commit.

Notification failures must never roll back wallet deductions, refunds, order state changes, or historical correction operations.

**BR-NOT-02 — Customer notification triggers:**
- Low balance warning (balance < ₹200)
- Order generation blocked (insufficient balance)
- Delivery confirmation (manually triggered by admin)
- Subscription cancelled
- Wallet credited
- Subscription auto-paused due to product being disabled

Customer recharge-request actions are operational notifications only.

They:
- do NOT create `admin_audit_log` entries
- do NOT create `wallet_ledger` entries
- only trigger admin notification workflows

**BR-NOT-03 — Admin notification triggers:**
- Customer with low balance (balance < ₹200)
- Customer with blocked order generation (insufficient balance)
- Scheduler job failure
- Product auto-pause event (when a product is disabled and subscriptions are auto-paused)

---

## 17. Customer Account Management

**BR-ACC-01**
Admin may deactivate a customer account (soft delete). Deactivated customers are hidden from operational views but all their data — orders, wallet, ledger history — is fully retained.

**BR-ACC-02**
When a customer is deactivated:
- ACTIVE subscriptions transition to PAUSED
- PENDING_START subscriptions transition to PAUSED
- Already-generated SCHEDULED orders remain unchanged and fulfillable
- Existing LOCKED orders remain unchanged and fulfillable
- Future order generation stops

Subscriptions paused due to customer deactivation must retain pause_reason = CUSTOMER_DEACTIVATED.

**BR-ACC-03**
Hard deletion of customer accounts is not supported.

**BR-ACC-04**
Customers cannot access other customers' data under any circumstances. Ownership validation is enforced at the service layer on every request.

---

## 18. Audit Logging

**BR-AUD-01**
All admin mutations are audit logged. This includes: wallet credits/adjustments, order overrides, subscription edits, historical corrections, manual status changes, customer deactivations, and scheduler reruns.

**BR-AUD-02**
Each audit log entry records: `acting_admin`, `action_type`, `target_entity`, `target_id`, `old_value` (JSONB), `new_value` (JSONB), `timestamp`, and an optional `notes` field for admin reasoning.

**BR-AUD-03**
Audit log entries are immutable and retained forever.

---

## 19. General Constraints

**BR-GEN-01**
Operational business entities (customers, subscriptions, products, orders, wallet ledger, audit logs) are never hard deleted.

Administrative configuration entities may support hard deletion where explicitly stated.

In MVP, business_holidays is the only entity that supports hard deletion.

Only future business_holiday records may be hard deleted.

Historical holiday records must remain immutable once their holiday_date has passed.

**BR-GEN-02**
Business logic lives in the service layer only. Controllers handle routing and input validation only.

**BR-GEN-03**
All inter-service calls use constructor injection. No field injection or setter injection.

**BR-GEN-04**
Database schema changes are managed via Flyway migrations only. No ad-hoc schema changes.

**BR-GEN-05**
REST APIs only. No GraphQL, no WebSockets, no event streaming.

**BR-GEN-06**
Single VPS deployment. Monolithic backend. No microservices, no Redis, no Kubernetes.
