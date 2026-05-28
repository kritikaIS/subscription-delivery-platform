# Juice Subscription & Delivery Platform — Backend

A production-style Spring Boot backend for a subscription-based juice delivery service. Covers the full operational lifecycle: customer onboarding, subscription management, nightly order generation, delivery execution, wallet ledger accounting, and admin operations — all built with strict transactional correctness, idempotent schedulers, and append-only financial records.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Security | Spring Security (stateless JWT) |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL 15 |
| Migrations | Flyway |
| Build | Maven |
| JWT | JJWT 0.12.6 |
| Google Auth | Google API Client 2.7 |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Testing | JUnit 5 + Testcontainers (PostgreSQL) |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    REST Controllers                      │
│  Auth · Customer · Onboarding · Subscription · Order    │
│  Wallet · Product · Admin (Orders/Wallet/Products/...)  │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│                    Service Layer                         │
│  AuthService · CustomerService · SubscriptionService    │
│  OrderGenerationService · OrderFreezeService            │
│  DeliveryService · OrderCorrectionService               │
│  WalletService · ProductService · AuditLogService       │
│  DeliverySheetService · BusinessHolidayService          │
│  SubscriptionActivationService · NotificationService    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│              Repositories (Spring Data JPA)             │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│              PostgreSQL 15 (Flyway migrations)          │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                  Nightly Schedulers (IST)               │
│  22:00 OrderFreezeJob                                   │
│  22:04 SubscriptionActivationJob                        │
│  22:05 OrderGenerationJob                               │
│  22:10 DeliverySheetGenerationJob                       │
└─────────────────────────────────────────────────────────┘
```

Key design principles:
- **Stateless JWT authentication** — no server-side session state
- **Append-only wallet ledger** — balance is never stored as a mutable column; every financial event inserts a new row
- **Idempotent schedulers** — all nightly jobs are safe to rerun; duplicate orders and delivery records are prevented by idempotency keys and DB constraints
- **Startup recovery** — on boot, the previous 3 calendar days are checked and any missed scheduler jobs are rerun in chronological order
- **Transactional financial operations** — delivery confirmation + wallet debit happen in a single ACID transaction with pessimistic row locking
- **PostgreSQL native enums** — all status fields use `CREATE TYPE ... AS ENUM` with `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`

---

## Database Schema

20 Flyway migrations, applied in order:

| Migration | Table / Change |
|---|---|
| V1 | All PostgreSQL enum types |
| V2 | `users` |
| V6 | `products` |
| V7 | `product_price_history` |
| V8 | `admin_credentials` |
| V9 | `refresh_tokens` |
| V10 | `delivery_addresses` |
| V11 | Seed test customer |
| V12 | `subscriptions` |
| V13 | `orders` |
| V14 | `wallet_ledger` |
| V15 | `delivery_records` |
| V16 | `scheduler_job_log` |
| V100 | Seed admin user |
| V101 | Add `HISTORICAL_CORRECTION_DEBIT` source type |
| V102 | `admin_audit_log` |
| V103 | `business_holidays` |
| V104 | `delivery_sheet_snapshots` |
| V105 | `recharge_request_log` |
| V106 | `subscription_change_requests` |

---

## API Reference

Base URL: `/api/v1`

### Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/customer/google` | None | Customer Google login — issues JWT + refresh token |
| POST | `/auth/customer/refresh` | None | Rotate customer refresh token |
| POST | `/auth/admin/login` | None | Admin phone + password login |
| POST | `/auth/admin/refresh` | None | Rotate admin refresh token |
| POST | `/auth/logout` | JWT | Revoke refresh token |

### Customer

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/onboarding` | Customer JWT | Complete onboarding (phone + address) |
| GET | `/customer/me` | Customer JWT | Get profile, address, and wallet summary |
| PUT | `/customer/address` | Customer JWT | Update delivery address immediately |

### Products

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/products` | Customer JWT | List available products |

### Subscriptions

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/subscriptions` | Customer JWT | Create subscription |
| GET | `/subscriptions` | Customer JWT | List my subscriptions |
| GET | `/subscriptions/{id}` | Customer JWT | Get subscription detail with pending changes |
| POST | `/subscriptions/{id}/pause` | Customer JWT | Pause subscription |
| POST | `/subscriptions/{id}/resume` | Customer JWT | Resume subscription |
| POST | `/subscriptions/{id}/cancel` | Customer JWT | Cancel subscription |
| POST | `/subscriptions/{id}/change-quantity` | Customer JWT | Submit quantity change request |
| POST | `/subscriptions/{id}/change-product` | Customer JWT | Submit product change request |
| GET | `/subscriptions/{id}/change-requests` | Customer JWT | List change request history |

### Orders

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/orders` | Customer JWT | List my orders |

### Wallet

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/wallet` | Customer JWT | Get wallet balance and summary |
| GET | `/wallet/ledger` | Customer JWT | Get ledger history |
| POST | `/wallet/recharge-request` | Customer JWT | Request wallet top-up (rate-limited: 1/hour) |

### Admin — Products

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/admin/products` | Admin JWT | List all products (including disabled) |
| POST | `/admin/products` | Admin JWT | Create product |
| PUT | `/admin/products/{id}` | Admin JWT | Update product |
| POST | `/admin/products/{id}/disable` | Admin JWT | Disable product (auto-pauses subscriptions) |
| POST | `/admin/products/{id}/enable` | Admin JWT | Re-enable product |

### Admin — Orders

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/admin/orders/{id}/deliver` | Admin JWT | Mark order delivered (atomic wallet debit) |
| POST | `/admin/orders/{id}/skip` | Admin JWT | Mark order skipped |
| PATCH | `/admin/orders/{id}` | Admin JWT | Historical correction (DELIVERED↔SKIPPED, LOCKED→CANCELLED) |

### Admin — Wallet

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/admin/customers/{id}/wallet/credit` | Admin JWT | Credit customer wallet |

### Admin — Holidays

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/admin/holidays` | Admin JWT | List business holidays |
| POST | `/admin/holidays` | Admin JWT | Add holiday |
| DELETE | `/admin/holidays/{id}` | Admin JWT | Delete future holiday |

### Admin — Delivery Sheets

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/admin/delivery-sheets/{date}` | Admin JWT | Get delivery sheet snapshot |
| GET | `/admin/delivery-sheets/{date}/download/csv` | Admin JWT | Download CSV |
| GET | `/admin/delivery-sheets/{date}/download/pdf` | Admin JWT | Download PDF |
| POST | `/admin/delivery-sheets/{date}/regenerate` | Admin JWT | Regenerate snapshot |

### Admin — Scheduler

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/admin/scheduler/freeze` | Admin JWT | Manually rerun OrderFreezeJob |
| POST | `/admin/scheduler/generate` | Admin JWT | Manually rerun OrderGenerationJob |
| POST | `/admin/scheduler/delivery-sheet` | Admin JWT | Manually rerun DeliverySheetGenerationJob |
| GET | `/admin/scheduler/history` | Admin JWT | View scheduler job history |

### Admin — Audit Logs

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/admin/audit-logs` | Admin JWT | Browse audit log entries |

### Health

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` or `/api/v1/health` | None | Health check |

---

## Scheduler Workflows

All schedulers run in Asia/Kolkata (IST) timezone.

```
22:00  OrderFreezeJob
       SCHEDULED orders → LOCKED
       Creates delivery_record(PENDING) per order

22:04  SubscriptionActivationJob
       PENDING_START subscriptions with start_date ≤ today → ACTIVE

22:05  OrderGenerationJob
       Runs SubscriptionActivation first (BR-ORD-07)
       For each ACTIVE subscription:
         - Applies APPROVED change requests (quantity/product)
         - Checks wallet balance
         - Creates SCHEDULED order with address + price snapshot

22:10  DeliverySheetGenerationJob
       Builds snapshot from LOCKED orders with PENDING delivery_records
       Stores as JSONB in delivery_sheet_snapshots
```

### Startup Recovery (BR-SCH-04)

On every application startup, the previous 3 calendar days are inspected. Any missed or failed jobs are rerun in chronological order, per day in sequence:

```
SubscriptionActivationJob → OrderGenerationJob → OrderFreezeJob → DeliverySheetGenerationJob
```

All recovery runs are fully idempotent — no duplicate orders, delivery records, or snapshots are created.

---

## Subscription Change Requests

Customers can schedule future changes to their subscriptions:

- **Quantity change** — takes effect on the next order generation after the effective date
- **Product change** — validated against product availability; takes effect on the next order generation

Rules:
- Requests are inserted directly as `APPROVED` (no pending state)
- Submitting a new request of the same type supersedes the previous `APPROVED` request
- One `APPROVED` quantity request and one `APPROVED` product request can coexist
- The scheduler applies `APPROVED` requests during `OrderGenerationJob` and marks them `APPLIED`
- If a `SCHEDULED` order already exists for the target date, it is updated inline before lock time

---

## Financial Integrity

- All monetary values stored as `BIGINT` in paise (₹1 = 100 paise)
- Wallet balance is never stored as a mutable column — computed from the latest `running_balance_paise` in `wallet_ledger`
- Every financial event inserts a new ledger row (append-only)
- Delivery confirmation + wallet debit are a single ACID transaction
- Pessimistic write lock (`SELECT FOR UPDATE`) on the latest ledger row before any balance mutation prevents concurrent double-deductions
- Historical corrections insert new ledger entries; existing rows are never modified

---

## Local Setup

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for PostgreSQL)

### 1. Clone

```bash
git clone <repo-url>
cd juice-platform
```

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL 15 on port `5433` with:
- Database: `juice_platform`
- User: `juice_user`
- Password: `juice_password`

### 3. Set Environment Variables

```bash
export JWT_SECRET="your-secret-key-at-least-32-characters-long"
export GOOGLE_CLIENT_ID="your-google-oauth-client-id"
```

### 4. Run the Backend

```bash
cd backend
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

To enable dev token endpoints (for local testing without Google OAuth):

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

> **Warning:** Never set `SPRING_PROFILES_ACTIVE=dev` in production. The `dev` profile exposes unauthenticated token generation endpoints at `/api/v1/dev/token/customer` and `/api/v1/dev/token/admin`.

### 5. Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Running Tests

Tests use Testcontainers — Docker must be running.

```bash
cd backend
mvn test
```

**176 tests, 0 failures.**

Test classes:

| Class | Coverage |
|---|---|
| `AuthServiceTest` | — |
| `CustomerServiceTest` | GET /me, PUT /address, address snapshot immutability |
| `SubscriptionServiceTest` | Create, pause, resume, cancel, ownership |
| `SubscriptionChangeRequestServiceTest` | Quantity/product changes, superseding, scheduler application |
| `OrderCorrectionServiceTest` | Historical corrections, auto-refund, negative balance |
| `DeliveryServiceTest` | Mark delivered/skipped, idempotency, wallet debit |
| `WalletServiceTest` | Credit, balance, ledger history |
| `WalletLedgerLockingTest` | Pessimistic locking, append-only invariant |
| `WalletRechargeRequestServiceTest` | Rate limiting, no wallet mutation |
| `AuditLogServiceTest` | Audit entries, filter by admin |
| `BusinessHolidayServiceTest` | Add/delete holidays, order generation skip |
| `DeliverySheetServiceTest` | Snapshot generation, rerun, juice summary |
| `ProductDisableAutoPauseTest` | Auto-pause on disable, SCHEDULED orders unchanged |
| `CriticalFixesTest` | Deactivated customer, SCHEDULED order cancellation |
| `AdminSchedulerServiceTest` | Rerun idempotency, job history, audit logging |
| `SchedulerJobLogTrackingTest` | RUNNING guard, COMPLETED/FAILED rerun, rowsProcessed |
| `SchedulerStartupRecoveryTest` | Missed job recovery, idempotency, 3-day window |
| `SecurityConfigTest` | CORS origins, no wildcard, credentials |
| `DevProfileIsolationTest` | DevAuthController absent outside dev profile |

---

## Configuration Reference

### `application.properties`

| Property | Description | Default |
|---|---|---|
| `server.port` | HTTP port | `8080` |
| `spring.datasource.url` | PostgreSQL JDBC URL | `jdbc:postgresql://127.0.0.1:5433/juice_platform` |
| `jwt.secret` | JWT signing secret (env: `JWT_SECRET`) | — |
| `jwt.access-token-expiry-minutes` | Access token TTL | `15` |
| `jwt.refresh-token-expiry-days` | Refresh token TTL | `30` |
| `google.client-id` | Google OAuth client ID (env: `GOOGLE_CLIENT_ID`) | — |
| `app.cors.allowed-origins` | Comma-separated allowed CORS origins | `http://localhost:3000,http://localhost:5173` |
| `scheduler.order-freeze.cron` | OrderFreezeJob cron (IST) | `0 0 22 * * *` |
| `scheduler.subscription-activation.cron` | SubscriptionActivationJob cron (IST) | `0 4 22 * * *` |
| `scheduler.order-generation.cron` | OrderGenerationJob cron (IST) | `0 5 22 * * *` |
| `scheduler.delivery-sheet.cron` | DeliverySheetGenerationJob cron (IST) | `0 10 22 * * *` |

---

## Security Notes

- `spring.profiles.active` is **not** hardcoded in `application.properties`. Set `SPRING_PROFILES_ACTIVE=dev` explicitly for local development only.
- CORS origins are configurable via `app.cors.allowed-origins`. Set to your actual frontend domain in production (e.g. `https://app.juiceplatform.com`).
- `JWT_SECRET` and `GOOGLE_CLIENT_ID` must be provided as environment variables. The application will fail to start without them.
- All admin mutations are audit-logged in `admin_audit_log` with before/after JSONB snapshots.

---

## Known Gaps (Not Yet Implemented)

The following API spec endpoints are defined in docs but not yet implemented:

- `GET /api/v1/orders/{id}` — customer order detail
- `GET /api/v1/admin/orders` and `GET /api/v1/admin/orders/{id}` — admin order listing
- `GET /api/v1/admin/subscriptions` and `PATCH /api/v1/admin/subscriptions/{id}` — admin subscription management
- `GET /api/v1/admin/subscriptions/{id}/change-requests` — admin change request view
- `POST /api/v1/admin/customers/{id}/deactivate` / `reactivate` — customer account management
- `GET /api/v1/admin/customers` and `GET /api/v1/admin/customers/{id}` — admin customer listing
- `POST /api/v1/admin/customers/{id}/wallet/adjust` and `set-balance` — manual wallet adjustments
- `GET /api/v1/admin/customers/{id}/wallet/ledger` — admin ledger view
- `POST /api/v1/admin/orders/{id}/confirm-email` — delivery confirmation email trigger
- Email delivery for all notifications (currently log-only stubs)
- Real PDF generation for delivery sheet download (currently plain-text placeholder)
