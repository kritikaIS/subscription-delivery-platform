# Juice Subscription & Delivery Platform — AI-Executable Backend Specification

> A solo-developer-oriented, production-grade specification for building a single-vendor juice recurring subscription and delivery management system.

---

## Table of Contents

1. [Comparable Platforms & Market Research](#1-comparable-platforms--market-research)
2. [Tech Stack Recommendation](#2-tech-stack-recommendation)
3. [System Architecture Overview](#3-system-architecture-overview)
4. [Database Modeling](#4-database-modeling)
5. [Subscription Engine Design](#5-subscription-engine-design)
6. [Scheduler & Automation Architecture](#6-scheduler--automation-architecture)
7. [Wallet / Ledger System](#7-wallet--ledger-system)
8. [Order Freeze & Effective-Date Logic](#8-order-freeze--effective-date-logic)
9. [Delivery Sheet Generation](#9-delivery-sheet-generation)
10. [Authentication & Session Management](#10-authentication--session-management)
11. [Security Architecture](#11-security-architecture)
12. [Concurrency & Transaction Rules](#12-concurrency--transaction-rules)
13. [Data Mutability Rules](#13-data-mutability-rules)
14. [Engineering Constraints](#14-engineering-constraints)
15. [API Contract Strategy](#15-api-contract-strategy)
16. [MVP vs. Advanced Scope](#16-mvp-vs-advanced-scope)
17. [UI/UX Recommendations](#17-uiux-recommendations)
18. [Deployment Guide for Solo Developer](#18-deployment-guide-for-solo-developer)
19. [Open-Source References](#19-open-source-references)

---

## 1. Comparable Platforms & Market Research

### Direct Analogues

**MilkBasket** (India) — the closest operational analogue. Customers place orders before midnight, deliveries happen before 7 AM. Subscription-first model, wallet-based payments, daily operational logistics. Key insight: their order cutoff system is nearly identical to your freeze logic.

**Country Delight** — farm-to-door model, subscription plans with pause/resume. Heavy emphasis on balance-based pre-paid accounts.

**Trakop / Milk Delivery Solutions** — a SaaS specifically built for this exact niche (single-vendor dairy delivery with subscription management, wallet balances). Study their feature set as a benchmark even if you build your own.

**DailyNinja** — subscription + one-time order hybrid, slot-based deliveries, balance credits.

**Akshayakalpa** — recurring subscriptions with pause/resume, no-minimum-order approach, wallet top-ups.

### SaaS Competitors to Study (Not Copy)

| Platform | What to Learn |
|---|---|
| Trakop | Delivery sheet formats, admin panel patterns |
| Milkride | SaaS model for dairy, admin panel patterns |
| ReCharge (Shopify) | Subscription state machine design patterns |
| Chargebee | Subscription lifecycle events, pause/resume modeling |
| Stripe Billing | Effective-date change handling |

### Key Insight from Market Research

Every successful platform in this niche solves the same three hard problems:
1. Order finalization cutoff (your "freeze" logic)
2. Balance consistency under concurrent deductions
3. Operational delivery sheet generation that accounts for same-day locked orders

Your platform is not unique in concept — it is well-proven. The challenge is clean implementation, not product discovery.

---

## 2. Tech Stack Recommendation

### Recommended Stack

| Layer | Choice | Rationale |
|---|---|---|
| Customer App | Responsive React PWA | Cross-platform iOS + Android + Web. Single codebase. Rich ecosystem. Excellent for subscription/wallet UIs. |
| Admin Dashboard | React + Vite | Fast iteration. Rich ecosystem (react-query, shadcn/ui, recharts). No SSR needed for admin. |
| Backend API | Spring Boot (Java 21) | Your existing preference is well-justified for this domain. Strong transaction management, mature scheduler libraries, excellent PostgreSQL support via JPA/Hibernate. |
| Database | PostgreSQL 15+ | Ideal for transactional ledger, complex queries, JSON columns for metadata, row-level security. All idempotency tracking, job logs, and session data stored here. |
| Background Jobs | Spring Scheduler (@Scheduled) | Built-in cron jobs with database-backed idempotency. Sufficient for this scale — no distributed scheduling needed. |
| Notifications | Email (SMTP) | Best-effort email notifications only. Failures are logged and never block business operations. |
| Deployment | Single VPS (Hetzner/DigitalOcean/Contabo) | Docker Compose. No Kubernetes for solo dev — too much ops overhead. |

### Is Spring Boot Ideal Here?

**Yes, with caveats.** For this specific domain (financial ledger consistency, complex transaction management, scheduled jobs, role-based security), Spring Boot's strengths are directly relevant:

- Transactional annotations make ledger deductions safe
- Spring Security with JWT handles two-role access (admin/customer) cleanly
- Spring's built-in @Scheduled cron jobs are production-proven for critical recurring jobs at this scale
- Spring Data JPA makes complex queries and database modeling manageable
- Java's type safety reduces bugs in financial logic

**Potential concern:** Spring Boot has significant startup overhead and memory footprint vs. NestJS or FastAPI. For a single-vendor system with modest traffic (~20 customers, ~30 deliveries/day), this is not a real-world problem — a 2 GB VPS handles it comfortably.

### Avoid for This Project

- Microservices architecture (too much DevOps overhead for solo dev)
- Serverless functions (cold starts hurt scheduled jobs)
- MongoDB (ACID transactions are critical for wallet ledger)
- GraphQL (REST is simpler for this CRUD + business logic mix)
- Redis (not required at this scale)

---

## 3. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│  Responsive React PWA (Customer)   React Dashboard (Admin)              │
└────────────┬───────────────────────┬────────────────────────────┘
             │ HTTPS + JWT           │ HTTPS + JWT
┌────────────▼───────────────────────▼────────────────────────────┐
│                     API GATEWAY / SPRING BOOT                   │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ Auth Module │  │ Customer API │  │    Admin API           │  │
│  │ (JWT/roles) │  │ /v1/customer │  │    /v1/admin           │  │
│  └─────────────┘  └──────────────┘  └───────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │             Core Business Logic Services                  │   │
│  │  SubscriptionService  |  WalletService  |  OrderService  │   │
│  │  DeliveryService      |  AuditLogService                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Scheduler Layer (@Scheduled cron jobs)       │   │
│  │  OrderFreezeJob  |  DeliverySheetGenerator           │   │
│  │  OrderGenerationJob  (incl. low-balance notifications)   │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────┬────────────────────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────────────────┐
│                     DATA LAYER                                  │
│  PostgreSQL (primary — all persistence)                         │
│  - Ledger (append-only)        - Scheduler job logs             │
│  - Subscriptions               - Idempotency tracking           │
│  - Orders (generated daily)    - Change request queue           │
│  - Delivery records            - Audit logs                     │
│  - Business holidays           - Refresh tokens                 │
└─────────────────────────────────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────────────────┐
│                  EXTERNAL SERVICES                              │
│  Google Sign-In (customer auth)    SMTP (email notifications)   │
└─────────────────────────────────────────────────────────────────┘
```

### Module Boundaries

Keep these as separate Spring service classes (NOT microservices — same JVM):

- **AuthService** — Google token verification, JWT generation, refresh, role resolution
- **CustomerService** — profile, address, preferences
- **SubscriptionService** — create/pause/resume/cancel, effective-date logic, change request processing
- **OrderService** — subscription-generated orders, freeze management, admin override
- **WalletService** — credit (admin only), debit (system after delivery only), ledger entries
- **DeliveryService** — delivery records, delivery sheet management
- **SchedulerService** — orchestrates nightly jobs
- **NotificationService** — email only (best-effort)
- **AuditLogService** — logs all admin actions

---

## 4. Database Modeling

### Core Tables

#### users

Stores all user types in a single table, distinguished by the `role` field.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key, auto-generated |
| phone | String (15) | Unique. Not mandatory at initial Google authentication — required to complete onboarding |
| name | String (100) | Not null |
| email | String (150) | Optional |
| role | Enum | ADMIN or CUSTOMER |
| google_id | String (255) | Google subject ID — populated for Google-authenticated customers |
| auth_provider | Enum | GOOGLE or ADMIN_PASSWORD |
| phone_verified | Boolean | Defaults to false. Phone verification is optional and manually handled by admin. This field is **informational/admin trust only** — it does NOT affect subscriptions, deliveries, or wallet behavior. Admin may manually mark a customer as verified. |
| email_verified | Boolean | Defaults to false |
| is_active | Boolean | Defaults to true |
| onboarding_completed | Boolean | Defaults to false. Set to true once phone number and delivery address are provided. Incomplete users cannot access customer business APIs. |
| created_at | Timestamp | Auto-set on insert |
| updated_at | Timestamp | Auto-updated |

> **auth_provider enum values:** `GOOGLE`, `ADMIN_PASSWORD`

> **Phone number rule:** Phone is NOT mandatory at initial Google authentication. However, onboarding cannot complete without a phone number and delivery address.

> **Customer deactivation:** Setting `is_active = false` immediately pauses all `ACTIVE` and `PENDING_START` subscriptions while preserving already-generated `SCHEDULED` orders. Future order generation stops until the customer is reactivated.

> **Customer reactivation:** Reactivating a customer restores account access only. Previously paused subscriptions remain paused until manually resumed.

---

#### delivery_addresses

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| customer_id | UUID | Foreign key → users. **UNIQUE constraint enforces one address per customer.** |
| line1 | String (255) | **Mandatory.** Not null |
| line2 | String (255) | Optional |
| city | String (100) | **Mandatory.** Not null |
| state | String (100) | **Mandatory.** Not null |
| pincode | String (10) | **Mandatory.** Not null |
| delivery_notes | Text | Optional customer instructions (e.g. "Leave at gate", "Ring bell once") |

> **One address per customer.** `UNIQUE(customer_id)` is enforced. A customer may update their single delivery address but cannot have multiple addresses.

> **Address model is structured.** The address is stored as discrete fields (`line1`, `line2`, `city`, `state`, `pincode`) matching the API spec. The `label`, `latitude`, and `longitude` columns are out of scope for MVP and are not included in this schema or any API response.

---

#### products

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| name | String (100) | Not null |
| description | Text | Optional |
| price_per_unit_paise | BIGINT | Not null. Stored in paise (integer). Always reflects current price — used for all future order generation |
| unit_label | String (20) | e.g. "500ml", "1 bottle" |
| category | String (50) | Optional grouping |
| is_available | Boolean | Defaults to true. Admin can disable/re-enable. |
| image_url | Text | Optional |
| sort_order | Integer | Controls display order |

> **Price policy:** `price_per_unit_paise` is the live price stored in paise (BIGINT). All active subscriptions and future order generation use the current price. There is no locked subscription pricing — a price change applies to all future orders immediately.

> **Disabled product behavior:** When `is_available = false`:
> - The product is hidden from the customer app and cannot receive new subscriptions
> - All `ACTIVE` and `PENDING_START` subscriptions for this product are automatically transitioned to `PAUSED`
> - Admin is notified; customers are notified of the auto-pause
> - Disabling a product does not cancel existing LOCKED or SCHEDULED orders. It only prevents future order generation.
>
> When a product is re-enabled (`is_available = true`):
> - Subscriptions remain `PAUSED` — they are **not** automatically resumed
> - Admin or customer must manually resume each affected subscription
> - Auto-resume on product re-enable is explicitly NOT supported
>
> **Products are NEVER hard deleted.**

#### product_price_history

Tracks every price change for audit purposes. A row is automatically inserted into this table whenever an admin updates a product's price via `PUT /api/v1/admin/products/{id}`. No separate endpoint or explicit insert is needed — the price history insert is part of the product update transaction.

> **No read endpoint exists in MVP.** The table is insert-only for historical audit purposes. A read endpoint may be added in a later phase.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| product_id | UUID | Foreign key → products |
| old_price_paise | BIGINT | Price before change, in paise |
| new_price_paise | BIGINT | Price after change, in paise |
| changed_by | UUID | Foreign key → users (admin) |
| changed_at | Timestamp | Auto-set on insert |

---

### Delivery Window Model

The system uses a single hardcoded operational delivery window: `"Morning"`.

> **Application constant:** Define `DELIVERY_WINDOW_NAME = "Morning"` as a named constant in the application layer. Do not inline the string literal in multiple places.

There is no `delivery_slots` table, no slot CRUD, and no slot assignment workflow.

All subscriptions and orders implicitly belong to this single operational delivery window.

---

#### business_holidays

Admin-defined days when no deliveries/orders are generated.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| holiday_date | LocalDate | Not null, unique |
| name | String (100) | e.g. "Diwali", "Republic Day" |
| created_by | UUID | Foreign key → users (admin) |
| created_at | Timestamp | Auto-set |

---

#### subscriptions — The Heart of the System

Allowed status values: `ACTIVE`, `PAUSED`, `CANCELLED`, `PENDING_START`

This table stores only the **current active state** of each subscription. Future scheduled changes are stored separately in `subscription_change_requests`.

A customer may have **multiple active subscriptions simultaneously** for **different products** (e.g., Orange Juice daily + Carrot Juice daily). Duplicate subscriptions for the same product are not allowed.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| customer_id | UUID | Foreign key → users |
| product_id | UUID | Foreign key → products |
| quantity | Integer | Minimum 1. Cannot be zero. Represents number of standard glasses defined by admin. |
| start_date | LocalDate | Not null |
| status | Enum | See allowed values above |
| pause_reason | ENUM | `USER_PAUSED`, `SYSTEM_PAUSED_PRODUCT_DISABLED`, `CUSTOMER_DEACTIVATED` — nullable |
| created_by | UUID | Foreign key → users — may be admin or customer |
| created_at | Timestamp | Auto-set |
| updated_at | Timestamp | Auto-updated |

> **Duplicate subscription constraint:** A partial unique index is enforced:
> ```
> UNIQUE(customer_id, product_id)
> WHERE status IN ('ACTIVE', 'PAUSED', 'PENDING_START')
> ```
> This prevents duplicate live subscriptions while still allowing historical `CANCELLED` subscriptions.

> **Quantity rules:** Minimum quantity is 1. Zero quantity is not allowed. Quantity represents the number of standard glasses as defined by admin.

> **No fixed end date or total_days.** Subscriptions continue automatically until the customer pauses/cancels or an admin disables them.

> **Subscriptions are NEVER hard deleted.** Cancellation only changes status to `CANCELLED`.

#### subscription_change_requests

Stores future scheduled changes to subscriptions.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| subscription_id | UUID | Foreign key → subscriptions |
| change_type | Enum | `QUANTITY`, `PRODUCT` |
| new_value | Text | Serialized new value. For `QUANTITY` changes: stores the integer quantity as a plain string (e.g. `"3"`). For `PRODUCT` changes: stores the new product UUID as a plain string (e.g. `"prod-uuid-2"`). No JSON wrapping. |
| effective_date | LocalDate | Date on which this change should be applied |
| status | Enum | `APPROVED`, `APPLIED`, `SUPERSEDED` — no `PENDING` state exists; requests are inserted directly as `APPROVED` |
| requested_by_type | Enum | `CUSTOMER`, `ADMIN` — not null |
| requested_by_user_id | UUID | nullable FK → users |
| created_at | Timestamp | Auto-set |

> **Supported change types:** `QUANTITY`, `PRODUCT`. ADDRESS changes are handled directly and do not go through the change request system.

> **Workflow:** All change requests are created as `APPROVED` immediately upon submission. The scheduler later applies `APPROVED` requests on the effective date, then marks them `APPLIED`. `APPLIED` status implies the change has been processed — no separate `processed` boolean is needed.

> **Conflict resolution:** If a newer request of the same `change_type` is submitted for the same subscription, the older request is immediately marked `SUPERSEDED` at the service layer during request creation — NOT during scheduler execution.

> **Concurrent change request types:** A subscription may simultaneously contain one APPROVED QUANTITY request and one APPROVED PRODUCT request. Supersedence occurs only within the same request type.

> **Change request eligibility:** Change requests are only allowed for subscriptions in `ACTIVE` or `PAUSED` state. Requests are not allowed for `PENDING_START` or `CANCELLED` subscriptions.

> **`PENDING_START` restriction:** Subscriptions awaiting activation cannot receive quantity or product change requests. If a customer wants modifications before activation, the subscription must be cancelled and recreated. Attempting to submit a change request on a `PENDING_START` subscription returns:
> ```json
> {
>   "success": false,
>   "error": {
>     "code": "SUBSCRIPTION_NOT_MODIFIABLE",
>     "message": "Changes are not allowed before subscription activation"
>   }
> }
> ```

> **Change request ownership:** Both CUSTOMER and ADMIN may create subscription change requests. Customers may only create requests for their own subscriptions. Admins may create requests on behalf of any customer (e.g. support calls, offline requests, operational corrections). Admin-created requests still follow normal effective-date logic, still create `subscription_change_requests` rows, and are still processed by the scheduler — they do NOT bypass the change request workflow. All requests flow through one unified mutation pipeline.

### Address Change Rules

- Address changes bypass the subscription change request system entirely
- Address updates apply **immediately** — no cutoff logic, no effective date, no future-scheduled row
- The next delivery always uses the customer's latest address at the time the order is generated
- Address changes do NOT create rows in `subscription_change_requests`
- **Already-generated orders retain the immutable address snapshot captured at order creation time.** Future customer address changes do not modify existing orders.

#### subscription_pause_history

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| subscription_id | UUID | Foreign key → subscriptions |
| pause_from | LocalDate | Not null |
| pause_until | LocalDate | Null means indefinite (paused until manually resumed) |
| reason | Text | Optional customer note |
| created_at | Timestamp | Auto-set |

> **Internal implementation detail.** The `subscription_pause_history` table is not exposed via any API endpoint. Pause records are never returned in any API response. The customer-facing effect of a pause is observable only through the subscription status and the `pauseEffectiveDate` field in the pause response. The `pause_until` field is not customer-settable — it is populated when the customer resumes (or left null for indefinite pauses).

> **Audit/history only.** The subscription_pause_history table is an audit/history record only. Subscription operational state is determined exclusively from subscriptions.status and subscriptions.pause_reason.

---

#### orders — Generated Daily from Subscriptions

Allowed status values: `SCHEDULED`, `LOCKED`, `DELIVERED`, `SKIPPED`, `CANCELLED`

**Order State Semantics:**
- `SCHEDULED` — generated, awaiting delivery day
- `LOCKED` — frozen at 10 PM cutoff; customer cannot modify
- `DELIVERED` — delivery confirmed; wallet deducted
- `SKIPPED` — delivery-day operational skip (customer unavailable, damaged item, operational issue). No balance deduction.
- `CANCELLED` — intentionally invalidated BEFORE delivery execution. Examples: subscription paused, subscription cancelled, product disabled. No balance deduction.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| customer_id | UUID | Foreign key → users |
| subscription_id | UUID | Foreign key → subscriptions — always populated |
| product_id | UUID | Foreign key → products |
| delivery_line1 | String (255) | Immutable snapshot — copied from customer address at order generation |
| delivery_line2 | String (255) | Immutable snapshot — copied from customer address at order generation. Optional. |
| delivery_city | String (100) | Immutable snapshot — copied from customer address at order generation |
| delivery_state | String (100) | Immutable snapshot — copied from customer address at order generation |
| delivery_pincode | String (10) | Immutable snapshot — copied from customer address at order generation |
| delivery_notes | Text | Immutable snapshot — copied from customer address at order generation. Optional. |
| delivery_date | LocalDate | Not null |
| quantity | Integer | Not null |
| unit_price_paise | BIGINT | Product's price in paise at time of order creation |
| total_amount_paise | BIGINT | quantity × unit_price_paise |
| status | Enum | See allowed values above |
| skip_reason | Enum | CUSTOMER_UNAVAILABLE, PRODUCT_UNAVAILABLE, DAMAGED, OTHER — populated when status is SKIPPED |
| cancellation_comment | Text | Optional internal admin note for CANCELLED orders. Not customer-visible. |
| cancellation_commented_by | UUID | Foreign key → users (admin). Populated when cancellation_comment is set. |
| cancellation_commented_at | Timestamp | Set when cancellation_comment is written. |
| idempotency_key | String (100) | Unique — e.g. "sub_\<id\>_2025-07-15". Prevents duplicate order generation. |
| notes | Text | Internal admin notes |
| created_at | Timestamp | Auto-set |

> **`notes` vs `cancellation_comment`:** `notes` = general internal admin memo. `cancellation_comment` = explanation specifically tied to CANCELLED order state.

> **SKIPPED orders never deduct balance.** `SKIPPED` is for delivery-day operational outcomes only (customer unavailable, damaged item, operational issue). The `skip_reason` field is for operational analytics only.

> **CANCELLED orders never deduct balance.** `CANCELLED` is for intentional pre-delivery business invalidation (subscription paused, subscription cancelled, product disabled). Do NOT use `CANCELLED` for insufficient-balance scenarios — those orders are never inserted at all.

> **No partial delivery or FAILED state.** The only delivery outcomes are DELIVERED and SKIPPED.

> **Money is stored as BIGINT paise. Floating point arithmetic is NEVER used for money calculations.**

> **Immutable address snapshot:** The delivery address fields (`delivery_line1`, `delivery_line2`, `delivery_city`, `delivery_state`, `delivery_pincode`, `delivery_notes`) are copied from the customer's profile at the time of order generation and remain immutable for the lifetime of the order. Future customer address updates never modify historical orders.

> **Address snapshot fields are copied during order generation and remain immutable for the lifetime of the order.**

---

#### refresh_tokens

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| user_id | UUID | Foreign key → users |
| token_hash | String (255) | Hashed refresh token value |
| expires_at | Timestamp | Expiry timestamp |
| revoked | Boolean | Defaults to false |
| created_at | Timestamp | Auto-set |

---

#### wallet_ledger — Append-Only

> **Critical rule:** Rows in this table are never updated or deleted. Every financial event is a new insert. Balance is always computed from the full history of entries.

> **Deduction timing:** Balance is deducted ONLY AFTER a delivery is marked `DELIVERED`, inside the same DB transaction as the delivery status update. No scheduled deduction job exists.

> **Money is stored as BIGINT paise. Floating point arithmetic is NEVER used for money calculations.**

Allowed entry types: `CREDIT` (admin adds balance), `DEBIT` (system deducts after confirmed delivery), `REFUND` (admin-issued reversal), `ADJUSTMENT` (manual correction)

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| customer_id | UUID | Foreign key → users |
| order_id | UUID | Foreign key → orders — order_id is populated for: DELIVERY_DEBIT entries, HISTORICAL_CORRECTION refund entries, and any ledger entry directly tied to a specific order. order_id is null for: admin credits, manual adjustments, and system adjustments. |
| entry_type | Enum | See allowed values above |
| amount_paise | BIGINT | Always a positive number in paise — direction is determined by entry_type |
| running_balance_paise | BIGINT | Stored for performance; live balance computed from latest ledger entry |
| description | Text | Human-readable note |
| reference | String (100) | UPI ref, cash receipt number, etc. |
| created_by_user_id | UUID (nullable) | Foreign key → users. Stores the admin ID for manual operations. Stores the admin ID who marked DELIVERED for deductions. Null for system/scheduler-generated entries. |
| source_type | Enum | `ADMIN_CREDIT`, `DELIVERY_DEBIT`, `REFUND`, `MANUAL_DEBIT`, `MANUAL_ADJUSTMENT`, `HISTORICAL_CORRECTION`, `SYSTEM_ADJUSTMENT` |
| created_at | Timestamp | Auto-set |
| — | Unique constraint | (order_id, source_type) — prevents duplicate deductions for the same order |

> **`source_type` rules:**
> - `ADMIN_CREDIT` — admin manually credits wallet; `created_by_user_id` = admin ID
> - `DELIVERY_DEBIT` — triggered when admin marks order DELIVERED; `created_by_user_id` = admin ID who marked delivery
> - `REFUND` — admin-issued reversal; `created_by_user_id` = admin ID
> - `MANUAL_DEBIT` — admin-issued manual deduction; `created_by_user_id` = admin ID
> - `MANUAL_ADJUSTMENT` — admin correction; `created_by_user_id` = admin ID
> - `HISTORICAL_CORRECTION` — admin corrects a historical record; `created_by_user_id` = admin ID
> - `SYSTEM_ADJUSTMENT` — system-generated entry; `created_by_user_id` = null

> **Balance update behavior:** Balance updates immediately when admin marks order DELIVERED. The balance deduction occurs inside the same DB transaction as the delivery status update.

> **Live balance:** Computed from the `running_balance_paise` of the latest ledger entry. Shown to the customer immediately after delivery confirmation.

> **Empty wallet:** If no `wallet_ledger` entries exist for a customer, computed balance defaults to 0 paise.

> **`running_balance_paise` is derived state and may be recomputed from full ledger history if required.** Ledger history is the true financial source-of-truth; `running_balance_paise` is a performance optimization only.

---

#### delivery_records

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| order_id | UUID | Foreign key → orders, unique (one record per order) |
| delivery_date | LocalDate | Not null |
| delivery_window | String | Hardcoded as "Morning" — single system delivery window |
| status | Enum | PENDING, DELIVERED, SKIPPED, CANCELLED |
| skip_reason | Enum | CUSTOMER_UNAVAILABLE, PRODUCT_UNAVAILABLE, DAMAGED, OTHER — populated when SKIPPED |
| delivered_at | Timestamp | Set when marked as delivered |
| notes | Text | Optional |
| photo_proof_url | Text | Optional — future feature |

> **Delivery outcomes are binary:** DELIVERED or SKIPPED. No partial delivery. No FAILED state. SKIPPED covers: customer unavailable, damaged delivery, operational issue.

> **Write path — INSERT vs UPDATE:** A `delivery_records` row is **inserted** during `OrderFreezeJob` for every order being locked (status `SCHEDULED` → `LOCKED`). The row starts with `status = PENDING`. When admin subsequently marks the order `DELIVERED` or `SKIPPED`, the existing `delivery_records` row is **updated** accordingly (status set to `DELIVERED` or `SKIPPED`). No `delivery_records` row is ever inserted at delivery time — only updated.
>
> Orders cancelled before reaching LOCKED state never create delivery records.
>
> If a previously LOCKED order is historically corrected to CANCELLED, the existing delivery_record row is retained and its status transitions to CANCELLED.

> When status is set to `DELIVERED`, the system triggers balance deduction inside the same DB transaction. When set to `SKIPPED`, no balance deduction occurs.

---

#### delivery_sheet_snapshots — Nightly Snapshot Table

Generated nightly; treated as **immutable** after generation.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| delivery_date | LocalDate | Not null |
| generated_at | Timestamp | Auto-set |
| generated_by | String | Defaults to "SCHEDULER" |
| snapshot_json | JSON | Full delivery sheet snapshot — includes delivery list AND `juiceSummary` |
| — | Unique constraint | (delivery_date) — one snapshot per day |

---

#### admin_audit_log

Tracks all admin actions for accountability. **Retained forever. Exportable.**

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| action_type | String (100) | e.g. BALANCE_CREDIT, ORDER_OVERRIDE, SUBSCRIPTION_EDIT, HISTORICAL_ORDER_EDIT, HISTORICAL_DELIVERY_EDIT, MANUAL_STATUS_CORRECTION, CUSTOMER_DEACTIVATION, SCHEDULER_RERUN |
| target_entity | String (50) | e.g. "order", "subscription", "customer" |
| target_id | VARCHAR(255) | ID or identifier of the affected record — supports UUID entities, scheduler jobs, and operational system actions |
| old_value | JSONB | JSON snapshot of the record before the change |
| new_value | JSONB | JSON snapshot of the record after the change |
| acting_admin | UUID | Foreign key → users (admin who performed the action) |
| notes | Text | Optional admin note or reason explaining the action |
| created_at | Timestamp | Auto-set |

> **Snapshot storage:** `old_value` and `new_value` are stored as PostgreSQL JSONB columns. Audit logs are retained forever and are exportable from the admin dashboard.

> **Audit coverage includes:** balance credits, order overrides, subscription edits, product changes, delivery changes, historical order edits, historical delivery edits, manual status corrections, customer deactivation, and scheduler reruns.

---

#### scheduler_job_log

Tracks scheduler executions and enforces idempotent daily job execution.

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| job_name | String (100) | Not null |
| job_date | LocalDate | Operational date for the job |
| status | Enum | RUNNING, COMPLETED, FAILED |
| started_at | Timestamp | Auto-set when job begins |
| finished_at | Timestamp | Nullable until completion |
| rows_processed | Integer | Number of processed records |
| error_message | Text | Nullable failure details |
| created_at | Timestamp | Auto-set |

> **Unique constraint:** `(job_name, job_date)`

> **Purpose:** Prevent duplicate execution of idempotent scheduled jobs.

---

### Recommended Database Indexes

| Table | Index |
|---|---|
| orders | (delivery_date, status) |
| orders | (customer_id, delivery_date) |
| subscriptions | (customer_id, status) |
| wallet_ledger | (customer_id, created_at DESC) |
| subscription_change_requests | (subscription_id, effective_date, status) |
| delivery_records | (delivery_date, status) |
| admin_audit_log | (created_at DESC) |
| scheduler_job_log | (job_name, job_date) UNIQUE |

---

### Entity Relationship Summary

| Relationship | Cardinality |
|---|---|
| users → subscriptions | One-to-many (customer can have multiple active subscriptions for different products) |
| users → orders | One-to-many |
| users → wallet_ledger | One-to-many |
| users → delivery_addresses | One-to-one (UNIQUE constraint enforces single address) |
| subscriptions → orders | One-to-many (generated daily) |
| subscriptions → subscription_pause_history | One-to-many |
| subscriptions → subscription_change_requests | One-to-many |
| orders → delivery_records | One-to-one (only for orders that reached LOCKED state; orders cancelled before LOCKED have no delivery record) |
| orders → wallet_ledger (DEBIT entry) | One-to-one (created only after DELIVERED) |
| products → subscriptions | One-to-many |
| business_holidays → order generation | Exclusion (no orders on holiday dates) |

---

## 5. Subscription Engine Design

### State Machine

#### Final Subscription States

| State | Description |
|---|---|
| `PENDING_START` | Created but effective date not yet reached |
| `ACTIVE` | Currently active and generating orders |
| `PAUSED` | Temporarily paused; no orders generated |
| `CANCELLED` | Terminal state; no reactivation possible |

#### Allowed State Transitions

| From | To |
|---|---|
| `PENDING_START` | `ACTIVE` |
| `PENDING_START` | `CANCELLED` |
| `ACTIVE` | `PAUSED` |
| `ACTIVE` | `CANCELLED` |
| `PAUSED` | `ACTIVE` |
| `PAUSED` | `CANCELLED` |

#### State Machine Rules

- `CANCELLED` is a **terminal state** — cancelled subscriptions can never be reactivated
- `PENDING_START` transitions to `ACTIVE` during `OrderGenerationJob` once the `start_date` (effective date) is reached — the transition is scheduler-driven, not event-driven
- Subscriptions created **before 10 PM IST** activate on the next eligible delivery date (tomorrow)
- Subscriptions created **after 10 PM IST** activate on the next eligible delivery date (day-after-tomorrow)

> There is no `COMPLETED` state from auto-expiry. Subscriptions run indefinitely until the customer or admin explicitly pauses or cancels.

### Subscription Lifecycle Rules

**Creation:**
- `start_date` is computed using the standard 10 PM IST cutoff: before cutoff → tomorrow; after cutoff → day-after-tomorrow
- Pricing is NOT locked at creation — all orders use the product's current price at time of order generation
- Admin can create subscriptions on behalf of customers
- Customers can self-create subscriptions (public/self-service)
- A customer may have multiple simultaneous active subscriptions for **different** products
- Duplicate subscriptions (same customer + same product) are NOT allowed — enforced via a partial unique index on `(customer_id, product_id)` where `status IN ('ACTIVE', 'PAUSED', 'PENDING_START')`

**Modification with Effective Date:**
- Quantity and product changes before cutoff → effective tomorrow
- Quantity and product changes after cutoff → effective day-after-tomorrow
- All change requests are stored as `APPROVED` immediately and applied on the effective date by the scheduler
- A nightly scheduler job fetches `APPROVED` records where `effective_date <= today`, applies them to the subscription, then marks them `APPLIED`
- **Address changes bypass this system entirely** — see Address Change Rules below

**Conflict Resolution — Latest Wins:**
- If two `APPROVED` requests of the same `change_type` exist for the same subscription, the latest request wins
- The earlier request is marked `SUPERSEDED`

**Pause Logic:**
- Customer initiates a full subscription pause (no individual-date skipping)
- A pause record is inserted into `subscription_pause_history` with `pause_from` date
- Existing `LOCKED` orders remain **unchanged** when a pause is initiated
- Future `SCHEDULED` orders are set to `CANCELLED` (system note = `subscription_paused`)
- No new orders are generated during the pause window
- `pause_until` may be null (indefinite pause until manually resumed)

**Resume:**
- Admin or customer resumes — subscription becomes `ACTIVE` from next eligible delivery date
- Same 10 PM IST cutoff logic applies to resume effective date
- Cancelled orders during the pause window are **never regenerated** — no historical backfilling
- Order generation resumes forward only

**Cancellation:**
- Soft delete: status = `CANCELLED`
- `LOCKED` orders remain **unchanged** — no balance refund for locked orders
- Future `SCHEDULED` orders are set to `CANCELLED` (system note = `subscription_cancelled`)
- No new future orders are generated after cancellation
- Admin may still override locked orders after cancellation

### Change Request Workflow

```
Customer or admin submits a change request
  → subscription_change_requests row inserted:
      change_type   = QUANTITY | PRODUCT
      status        = APPROVED  ← immediately approved on creation
      effective_date = computed from 10 PM cutoff

Scheduler picks up APPROVED requests where effective_date <= today
  → applies change to subscription
  → marks request status = APPLIED
  → If matching SCHEDULED order exists for effective date:
      update order inline before freeze

If a newer request of the same change_type is submitted before the first is applied:
  → older request status = SUPERSEDED (at service layer, during creation of new request)
```

### Order Generation Algorithm (Nightly Job)

```
FOR each active subscription S:
  target_date = next operational delivery date  -- default; may be explicitly provided during manual scheduler reruns

  -- Phase 1 — Always execute:
  -- apply PENDING_START → ACTIVE transitions
  -- apply eligible change requests

  -- Skip if customer is deactivated
  IF customer.is_active == false:
    SKIP subscription

  -- Skip if subscription is paused
  IF subscription_paused(S.id, target_date):
    SKIP

  -- Apply approved pending changes if effective_date <= target_date
  pending = fetch_approved_changes(S.id, target_date)
  FOR each change in pending:
    apply_change_to_subscription(S.id, change)
    mark_change_applied(change.id)

  IF order already exists for target operational delivery date:
    UPDATE existing SCHEDULED order
    recalculate totals
    CONTINUE

  -- Check idempotency
  idempotency_key = "sub_" + S.id + "_" + target_date
  IF order_exists(idempotency_key):
    SKIP  -- already generated; job ran twice, handle gracefully

  -- Phase 2 — Business-day-only execution:
  IF is_business_holiday(target_date):
    SKIP order generation

  ELSE:
    -- Check if product is available
    IF product_unavailable(S.product_id):
      notify_admin(product_unavailable)
      -- This is a defensive validation only.
      -- In normal operation, subscriptions for disabled products should already be PAUSED
      -- and should never reach this step.
      SKIP

    -- Fetch current product price in paise (no locked pricing)
    current_price_paise = get_current_price_paise(S.product_id)
    order_cost_paise    = S.quantity * current_price_paise

    -- Check customer balance BEFORE generating order
    balance_paise = get_balance_paise(S.customer_id)

    -- These are two separate, independent checks — do NOT conflate them

    -- CHECK 1: Low Balance Warning (informational only)
    IF balance_paise < 20000:  -- ₹200 warning threshold (in paise)
      notify_low_balance_warning(S.customer_id)  -- customer + admin notified; deliveries continue

    -- CHECK 2: Order Generation Block
    IF balance_paise < order_cost_paise:
      notify_insufficient_balance(S.customer_id)  -- customer + admin notified
      -- NO order row is created — do NOT insert a CANCELLED order
      -- Subscription remains ACTIVE; recovery requires admin to rerun OrderGenerationJob after topping up balance
      SKIP

    CREATE order (
      customer_id, subscription_id, product_id,
      delivery_line1    = fetch_address_field(S.customer_id, 'line1'),
      delivery_line2    = fetch_address_field(S.customer_id, 'line2'),
      delivery_city     = fetch_address_field(S.customer_id, 'city'),
      delivery_state    = fetch_address_field(S.customer_id, 'state'),
      delivery_pincode  = fetch_address_field(S.customer_id, 'pincode'),
      delivery_notes    = fetch_address_field(S.customer_id, 'delivery_notes'),
      delivery_date   = target_date,
      quantity,
      unit_price_paise  = current_price_paise,
      total_amount_paise = order_cost_paise,
      status          = SCHEDULED,
      idempotency_key
    )

-- Run this in a SINGLE TRANSACTION per subscription
-- Never batch all subscriptions in one transaction (lock contention)
```

> **Deactivated Customer Check:** If `customer.is_active == false`, the scheduler skips all subscriptions for that customer without generating any orders. Deactivating a customer immediately pauses all `ACTIVE` and `PENDING_START` subscriptions while preserving already-generated `SCHEDULED` orders — the scheduler will not generate new orders for them until the customer is reactivated. This keeps scheduler behavior operationally safe.

> **Reactivation behavior:** Reactivating a customer restores account access only. Previously paused subscriptions remain paused until manually resumed.

> **Low Balance — Dual Independent Checks:**
>
> **Check 1 — Low Balance Warning:** Triggered when `balance < ₹200`. Customer and admin are notified. This is informational only — deliveries continue as long as balance covers the order cost.
>
> **Check 2 — Order Generation Block:** Triggered when `balance < upcoming order cost`. No order row is created. Customer and admin are notified. Subscription remains `ACTIVE`. If the admin later credits the wallet, the admin must manually rerun `OrderGenerationJob` — the job is idempotent and will safely generate the previously blocked order.
>
> These are separate, independent checks. Check 1 does not stop orders. Check 2 does. They must not be conflated.
>
> Negative balance is NOT supported; there is no overdraft.

### Notification Triggers

All notifications are email-only, best-effort, and non-blocking. Failures are logged and never affect business operations.

**Customer notifications:**
- Low balance warning (balance < ₹200)
- Order generation blocked (insufficient balance)
- Delivery confirmation (manually triggered by admin)
- Subscription cancelled
- Wallet credited
- Subscription auto-paused due to product being disabled

**Admin notifications:**
- Customers with low balance (balance < ₹200)
- Customers with blocked order generation (insufficient balance)
- Scheduler job failure
- Product auto-pause event (when a product is disabled and subscriptions are auto-paused)

> **Delivery confirmation emails are manually triggered by the admin.** They are never sent automatically.

---

### Final Nightly Jobs

All jobs run in the **Asia/Kolkata (IST) timezone**. Each must be **idempotent** — running twice must produce the same result as running once.

| Job | Time (IST) | Description |
|---|---|---|
| `OrderGenerationJob` | 10:05 AM | Generates next operational delivery date orders from active subscriptions; also handles `PENDING_START → ACTIVE` transitions and low-balance notifications |
| `OrderFreezeJob` | 10:00 PM | Locks all eligible SCHEDULED orders |
| `DeliverySheetGenerationJob` | 10:10 PM | Generates nightly delivery sheet snapshot (delivery list + juice prep summary) |

> **Orders are generated exactly one operational delivery date ahead.** No multi-day pre-generation.

> **`OrderGenerationJob` also handles `PENDING_START → ACTIVE` transitions.** During the job run, any subscription in `PENDING_START` whose effective date has been reached is transitioned to `ACTIVE` before order generation proceeds.

> **`BalanceDeductionJob` does not exist as a scheduled job.** Balance deduction is triggered in real time when a delivery record is marked `DELIVERED` — not by a separate batch job.

### Idempotency Pattern

Every job must use an idempotency log stored in the database:

| Column | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| job_name | String (100) | Not null |
| job_date | LocalDate | Not null |
| status | String | RUNNING, COMPLETED, or FAILED |
| started_at | Timestamp | When job began |
| finished_at | Timestamp | When job completed |
| rows_processed | Integer | How many records were handled |
| error_message | Text | Populated on failure |
| — | Unique constraint | (job_name, job_date) — one execution per job per day |

Before any job runs:

```
ATTEMPT insert scheduler_job_log(status = RUNNING)

IF conflict on (job_name, job_date):
  FETCH existing row

  IF existing.status = RUNNING:
    EXIT -- job already in progress

  IF existing.status IN (COMPLETED, FAILED):
    DELETE existing row
    INSERT new RUNNING row
```

### Scheduler Failure Recovery

If the server is down during nightly jobs:

- Missed jobs auto-rerun on startup
- Admin can manually rerun any job from the dashboard
- All jobs remain idempotent — rerunning is safe
- Duplicate order generation is prevented through idempotency keys

> Admins may manually rerun scheduler jobs for historical operational dates. All reruns must remain idempotent and audit logged.

### Scheduler Implementation Approach

```
SCHEDULE OrderGenerationJob          → runs at 10:05 AM IST every day
SCHEDULE OrderFreezeJob              → runs at 10:00 PM IST every day
SCHEDULE DeliverySheetGenerationJob  → runs at 10:10 PM IST every day
```

All times are anchored to the Asia/Kolkata timezone.

---

## 7. Wallet / Ledger System

### Core Principle: Append-Only Ledger

Never store balance as a mutable column on the customer record. Instead:

- Every financial event inserts a new row into `wallet_ledger`
- `running_balance_paise` is stored on each ledger row for performance
- Live balance = `running_balance_paise` of the latest ledger entry for the customer
- Customer balance is shown immediately after delivery confirmation
- **The customer balance is never mutated directly on a customer profile record.** All balance changes — including operational balance corrections — are implemented through append-only wallet_ledger entries. Admin "set balance" operations internally create `SYSTEM_ADJUSTMENT` ledger entries rather than modifying historical ledger rows.

### Final Ledger Entry Types

> **`entry_type` and `source_type` are two separate columns on `wallet_ledger`.** `entry_type` represents the broad financial direction (credit into or debit out of the wallet). `source_type` represents the operational origin of that entry (who or what triggered it). Do not merge them into one field.

| Entry Type | Triggered By |
|---|---|
| `CREDIT` | Admin manually credits wallet after external payment |
| `DEBIT` | System deducts after delivery is marked `DELIVERED` |
| `REFUND` | Admin-issued reversal |
| `ADJUSTMENT` | Manual correction by admin |

### Wallet Top-Up Flow

```
1. Customer pays externally (cash, UPI, etc.)
2. Admin manually credits wallet via admin dashboard
3. CREDIT ledger entry is inserted into wallet_ledger
4. Audit log entry is created
```

> **No payment gateway. No automatic recharge.** Minimum wallet credit amount is ₹1 (100 paise). No maximum limit exists. Admin may credit any amount at or above the minimum.

### Manual Corrections

Admin may insert `DEBIT` or `ADJUSTMENT` ledger entries for manual corrections. The customer balance is **never mutated directly on a customer profile record** — corrections are always appended as new ledger entries.

### Money Storage Rule

**All monetary values are stored as BIGINT paise (integer cents). Floating point arithmetic is NEVER used for money calculations.**

Examples: `price_per_unit_paise`, `amount_paise`, `running_balance_paise`

### Balance Deduction Flow

Deduction is triggered when a delivery record is marked `DELIVERED`. The deduction occurs **inside the same DB transaction** as the delivery status update.

```
-- Triggered by: admin marks delivery as DELIVERED

BEGIN TRANSACTION

  GET current balance for customer (with row-level lock to prevent race conditions)

  IF balance_paise < order.total_amount_paise
AND NOT isHistoricalCorrection:
    RAISE error

  IF a DEBIT entry for this order_id already exists:
    SKIP insert (idempotency — prevents double deduction)

  INSERT into wallet_ledger:
    customer_id, order_id, entry_type = DEBIT,
    source_type = DELIVERY_DEBIT,
    created_by_user_id = <admin id who marked DELIVERED>,
    amount_paise = order.total_amount_paise,
    running_balance_paise = previous_balance - order.total_amount_paise, ...

  UPDATE order status to DELIVERED
  UPDATE delivery_records status to DELIVERED

COMMIT TRANSACTION
```

### Delivery Skip Flow

```
-- Triggered by: admin marks delivery as SKIPPED

BEGIN TRANSACTION
  UPDATE delivery_records SET status = SKIPPED, skip_reason = <reason>
  UPDATE order SET status = SKIPPED, skip_reason = <reason>
  -- NO wallet_ledger entry is inserted
  -- NO balance deduction occurs
COMMIT TRANSACTION
```

### Admin Credit Flow

```
INSERT into wallet_ledger:
  customer_id         = <target customer>
  entry_type          = CREDIT
  source_type         = ADMIN_CREDIT
  amount_paise        = 300000  -- ₹3000 in paise
  description         = "Cash payment - 15 July"
  reference           = "CASH-20250715-001"
  created_by_user_id  = <admin user id>

-- Audit log entry automatically created

-- Post-commit (non-blocking):
notify_customer_wallet_credited(customer_id, amount_paise)
```

---

## 8. Order Freeze & Effective-Date Logic

### This Is the Most Critical Business Logic

The same **10 PM IST cutoff** applies to ALL future-effective operations:

- Subscription creation
- Quantity changes
- Product changes
- Pause requests
- Resume requests

> **Address changes are excluded from cutoff logic.** Address updates apply immediately and do not create future-effective change requests.

```
SCENARIO A: Action taken BEFORE 10 PM today
  → Effective TOMORROW (next delivery day)

SCENARIO B: Action taken AFTER 10 PM today
  → Effective DAY AFTER TOMORROW
  → Next Operational Delivery date's order is already LOCKED/FROZEN — customer cannot modify it

SCENARIO C: Customer cancels a subscription AFTER 10 PM
  → Next Operational Delivery date's order is LOCKED — not cancelled
  → Subscription moves to CANCELLED
  → Day-after-tomorrow onwards: no new orders generated

SCENARIO D: Admin needs to modify a locked order
  → Admin CAN override any locked order at any time
  → All admin overrides are written to admin_audit_log
```

### Effective Date Computation

```
FUNCTION computeEffectiveDate():
  now    = current time in IST  -- backend server time only; never client device time
  cutoff = 10:00 PM

  IF now is before cutoff:
    RETURN today + 1 day   -- effective tomorrow

  ELSE:
    RETURN today + 2 days  -- effective day-after-tomorrow
```

### Order Locking Job

```
FUNCTION lockTomorrowsOrders():
  tomorrow = today + 1 day

  UPDATE all orders WHERE:
    delivery_date = tomorrow
    AND status    = SCHEDULED

  SET:
    status    = LOCKED
```

### Admin Override

```
FUNCTION adminOverrideOrder(orderId, changes, adminId):
  order = fetch order by orderId

  apply changes to order (quantity, address, notes, status)

  IF quantity or product changed:
    IF only quantity changes:
      new_total = new_quantity × existing order.unit_price_paise

    IF product changes:
      new_unit_price = current product price
      new_total = quantity × new_unit_price
    -- Must complete before transaction commit

  INSERT into admin_audit_log:
    acting_admin  = adminId
    action_type   = ORDER_OVERRIDE
    target_entity = "order"
    target_id     = orderId
    old_value     = previous order snapshot (JSON)
    new_value     = updated order snapshot (JSON)
```

---

## 9. Delivery Sheet Generation

### What Gets Generated Nightly

After order freeze, the system generates a delivery sheet snapshot that contains **both**:

1. **Delivery List** — who gets what, where, and when
2. **`juiceSummary`** — total quantity of each product needed for the day's deliveries

### Storage Strategy

Store as a JSON snapshot in `delivery_sheet_snapshots`. Snapshots are **immutable** after generation.

> **Authoritative for API and export.** Delivery sheet snapshots are authoritative for API and export generation. If admin corrections occur after snapshot generation, admins must manually rerun DeliverySheetGenerationJob. Manual reruns overwrite the existing snapshot for that delivery date.

### Sheet Structure

```
delivery_date     → the date the deliveries are for
generated_at      → when this snapshot was created

juiceSummary
  └── product_name → total quantity needed

orders [ ]
  └── customer_name
  └── phone
  └── address
  └── delivery_notes
  └── items [ ]
        └── product_name
        └── quantity
```

### Admin Dashboard Views

- **Morning view**: mark deliveries as completed or skipped
- **Print view**: formatted for physical sheet — includes delivery notes per customer
- **Preparation view**: juice quantities to prepare before 6 AM (per product, full day)

---

## 10. Authentication & Session Management

### Customer Authentication

#### Google Authentication Flow

- Customers authenticate using **Google Sign-In only**
- Responsive React PWA obtains a Google ID token
- Backend verifies the Google ID token with Google's servers — verification is mandatory server-side
- Backend issues JWT access token + refresh token after successful Google verification
- Onboarding may remain incomplete initially after the first Google sign-in

#### Required Onboarding Fields

Customers must complete onboarding by providing:
- Phone number
- Delivery address

#### Onboarding Completion Flag

The `users` table includes an `onboarding_completed` BOOLEAN field (defaults to `false`).

This field is set to `true` once both required onboarding fields have been provided.

#### Incomplete Onboarding Restrictions

Users with `onboarding_completed = false` may **only**:
- Complete their onboarding (provide phone and address)
- Logout

Users with `onboarding_completed = false` **cannot**:
- Create subscriptions
- Access any customer business APIs

Phone numbers are **NOT OTP verified**. Phone verification is optional and manually handled by admin.

Customer onboarding is **public/self-service**.

### Admin Authentication

- Admin login uses phone number + password
- Admin password reset is handled operationally during MVP deployment
- Self-service forgot-password functionality is intentionally unsupported

### JWT Strategy

- **Access token expiry:** 15 minutes
- **Refresh token expiry:** 30 days
- Refresh tokens are stored in the `refresh_tokens` database table
- **Single active session per user** — a new login immediately revokes the previous refresh token, invalidating the previous session. Revocation is checked at token refresh time. Only the latest refresh token is valid per user at any time.
- Backend server time is the source of truth for token expiry — never client device time

### JWT Payload

| Field | Description |
|---|---|
| user_id | User's unique ID |
| role | ADMIN or CUSTOMER |
| phone | User's phone number |
| issued_at | Issued-at timestamp |
| expiry | Expiry timestamp |

### API Route Permissions

| Route Prefix | Who Can Access |
|---|---|
| /api/v1/auth/** | Public (login, Google callback, onboarding) |
| /api/v1/admin/** | ADMIN only |
| /api/v1/subscriptions/** | CUSTOMER owns access to their own resources; ADMIN may operationally access customer resources when required. |
| /api/v1/orders/** | CUSTOMER owns access to their own resources; ADMIN may operationally access customer resources when required. |
| /api/v1/wallet/** | CUSTOMER owns access to their own resources; ADMIN may operationally access customer resources when required. |
| /api/v1/products/** | CUSTOMER owns access to their own resources; ADMIN may operationally access customer resources when required. |
| /api/v1/onboarding | Public (onboarding completion) |

---

## 11. Security Architecture

### Two-Role Model

| Role | Trust Level | Access Scope |
|---|---|---|
| ADMIN | Fully trusted | Full access — manages everything, including locked order overrides |
| CUSTOMER | Semi-trusted | Own data only — subscriptions, orders, wallet |

### Customer Change Permissions

| Action | Customer Can Do | Requires Admin Approval |
|---|---|---|
| Change quantity | Yes (respecting cutoff) | No |
| Change delivery address | Yes (applies immediately) | No |
| Change product | Yes (respecting cutoff) | No |
| Pause subscription | Yes | No |
| Resume subscription | Yes | No |
| Cancel subscription | Yes | No |
| Skip individual dates | **Not supported** | N/A |

### Critical Security Rules

**Ownership enforcement:**

```
FUNCTION getOrder(orderId, authenticatedUserId):
  order = fetch order by orderId

  IF order.customer_id ≠ authenticatedUserId:
    RAISE forbidden error — "Access denied"

  RETURN order
```

Never rely on the client sending their own customer ID in the request body. Always extract identity from the JWT.

**Wallet credits — admin only:** The `creditBalance` function must only be callable by the ADMIN role. Validated at the service layer, not just the controller.

**All admin mutations must write to `admin_audit_log`.**

**Access control enforced at service layer.**

**Rate limiting:** Apply rate limiting on auth endpoints to prevent brute force. Implemented with a database-backed counter — no Redis required.

### Search & Filter Requirements

Admin dashboard supports:

- Customer search by name or phone
- Subscription filtering
- Delivery filtering by date

No advanced full-text search required.

---

## 12. Concurrency & Transaction Rules

- **Admin actions override scheduler actions**
- **Backend server time is the source of truth** — never client device time
- Scheduler jobs execute in isolated transactions
- Wallet deduction uses row-level locking
- Negative balance is forbidden during normal operational flows. Historical correction workflows may intentionally bypass this restriction.
- Balance deduction and delivery status update execute in the same DB transaction

---

## 13. Data Mutability Rules

### NEVER Editable (Immutable)

- `wallet_ledger` rows
- `scheduler_job_log` rows
- `delivery_sheet_snapshots` rows

### Editable By Admin (Must Be Audit Logged)

- Delivered orders (historical order edits)
- Delivery records (historical delivery edits)
- Subscriptions
- Products
- Customers

### Historical Corrections Policy

Admin may edit historical orders and historical delivery records for operational corrections. The following rules apply without exception:

- **Ledger entries remain immutable** — historical order edits do NOT automatically modify wallet balances in the general case
- **Financial corrections are handled manually** using explicit ledger entries (`ADJUSTMENT` or `DEBIT`)
- Historical operational corrections are intentionally decoupled from automatic ledger reconciliation, **with one explicit exception:**

> **Exception — `isSystemError` auto-refund path:** When an admin corrects a `DELIVERED` order to `SKIPPED` and sets `isSystemError = true` (indicating a system-level mistake such as a wrong confirmation or a glitch), the wallet deduction is automatically reversed via a `REFUND` ledger entry inserted in the same transaction. For non-system-error operational corrections (`isSystemError = false` or omitted), no automatic balance adjustment occurs — those are handled via manual ledger entries.

**Required audit logging for all historical edits:**
- Before snapshot (JSON)
- After snapshot (JSON)
- Acting admin ID
- Timestamp

> **Critical note on historical order edits:** Historical order edits are allowed only because the system is initially deployed in operational testing mode. All historical modifications MUST write an admin audit log entry containing before/after JSON snapshots, a timestamp, and the acting admin's ID.

> **Customers are NEVER hard deleted.** Use `is_active = false` for deactivation.

> **Products are NEVER hard deleted.** Use `is_available = false` for disabling.

---

### Delivery Confirmation Email Behavior

When admin presses "Send Delivery Confirmation":

1. System attempts email delivery to the customer
2. Result (success or failure) is returned to the UI immediately
3. Failures are logged internally

> **Email failures NEVER affect order or delivery state.** The delivery remains `DELIVERED` regardless of whether the confirmation email was successfully sent.

---

## 14. Engineering Constraints

These constraints are mandatory. Deviations are not permitted.

- **Layered architecture only** — no microservices
- **Constructor injection only** — no field injection
- **DTOs mandatory** — request and response DTOs are separate; no entity exposure
- **No business logic in controllers** — all business logic lives in the service layer
- **REST APIs only** — no GraphQL
- **PostgreSQL only**
- **Flyway migrations required** — all schema changes via Flyway
- **Flyway migrations are append-only** — old migrations are never edited after deployment
- **No Redis required**
- **Append-only ledger architecture** — wallet_ledger rows are never updated or deleted
- **All admin mutations audit logged** — no exceptions
- **Scheduler jobs must be idempotent**
- **Transactions mandatory for wallet deduction**
- **Access control enforced at service layer**
- **Google authentication verification mandatory at backend** — the PWA token is always re-verified server-side with Google
- **BIGINT paise for all money** — floating point is never used for financial calculations
- **Backend server time is source of truth** — never trust client timestamps

### Time Handling

| Type | Usage |
|---|---|
| Timestamp (Instant) | All timestamps (created_at, updated_at, etc.) |
| LocalDate | Delivery dates, holiday dates, effective dates |

**Timezone:** Asia/Kolkata only, server-side.

### Database Migration Policy

- Flyway is used for all schema versioning
- Migrations are append-only — V1, V2, V3, etc.
- Old migrations are never edited after deployment

---

## 15. API Contract Strategy

### API Versioning

- Base path: `/api/v1`
- Future breaking changes use `/api/v2`
- The current codebase targets `/api/v1` exclusively

### Pagination

All list endpoints use offset pagination:

| Parameter | Default | Max |
|---|---|---|
| `page` | `0` | — |
| `size` | `20` | `100` |

### Standard Error Response Format

All APIs return errors in this format:

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "details": {}
  }
}
```

- REST APIs only
- Request and response DTOs are strictly separated — entities are never serialized directly
- Validation rules enforced at DTO layer
- HTTP status code conventions followed consistently

OpenAPI spec will be maintained separately at `/docs/api-spec.md`.

---

## 16. MVP vs. Advanced Scope

### Phase 1 — MVP (2–3 months solo)

**Core functionality:**
- Admin: product management (including temporary disable/enable), customer management, balance management (manual credit), subscription creation for customers
- Admin: holiday management
- **Public customer onboarding via Google Sign-In** — self-service
- **Customer self-subscription creation included in MVP**
- Customer: view subscriptions, view balance, view upcoming deliveries, see pending future changes
- System: nightly order generation (respecting holidays, product availability, balance check), order freezing, daily delivery sheet
- Delivery: admin marks deliveries as DELIVERED or SKIPPED; balance deducted only on DELIVERED

**What to skip in MVP:**
- Customer self-service pause/resume UI deferred to Phase 2. Admin may still manage pause/resume operationally during MVP.
- Email notifications
- Photo proof of delivery

### Phase 2 — Customer Self-Service (1–2 months)

- Pause/resume (full subscription pause only)
- Balance view and ledger history
- Email notifications (low balance, delivery confirmation, subscription updates)

### Phase 3 — Operational Excellence (ongoing)

- Photo proof of delivery
- Automated low-balance alerts
- Manual delivery confirmation email (admin-triggered)

---

## 17. UI/UX Recommendations

### Customer App (Responsive React PWA)

**Home screen:**
- Today's upcoming delivery (prominent card with friendly delivery window label e.g. "Morning Delivery" — never the internal slot ID or slot name)
- Current wallet balance with quick "Alert Admin to Recharge" button
- All active subscriptions listed

**Subscription management:**
- Clear list of all active subscriptions — each manageable independently
- Timeline view: next 7 days with delivery/pause indicators
- "Pause" action pauses the full subscription (no individual day skipping)
- Changes show "Will apply from [date]" — never hide the effective date
- After 10 PM cutoff: "Changes will apply from [date — day after tomorrow]"

**Wallet screen:**
- Current balance (large, prominent)
- Transaction history: credits vs debits, color-coded
- Low balance warning banner when balance is below ₹200

### Admin Dashboard (React)

**Daily operations view:**
- Today's delivery summary
- Tomorrow's locked orders count
- `juiceSummary` for tomorrow (prominently displayed)
- Customers with zero/low balance

**Customer detail view:**
- Balance prominently displayed with quick credit button
- All active subscriptions listed with status, next delivery, pause status
- Full ledger history with filter by date/type

**Order sheet view:**
- Filter by date
- Delivery list per customer AND `juiceSummary`
- Delivery notes visible per customer
- Download as PDF / print-ready format
- Admin can override any order detail regardless of lock status

**Notification trigger:**
- Customer delivery confirmation emails are manually triggered by admin button press

**Reports available (downloadable from admin dashboard in PDF and CSV formats):**
- Daily delivery sheet
- `juiceSummary`
- Low balance customers
- Active subscriptions
- Delivery history

> **Reports are NOT emailed automatically.** All reports are downloaded on demand from the admin dashboard.

---

## 18. Deployment Guide for Solo Developer

### Recommended: Single VPS with Docker Compose

**Server specs:** Hetzner CX21 (2 vCPU, 4 GB RAM, 40 GB SSD) — ~€6/month.

**Docker Compose services:**

| Service | Image | Role |
|---|---|---|
| app | Your Spring Boot build | The main API server |
| db | PostgreSQL 15 | Primary database |
| nginx | Nginx (Alpine) | Reverse proxy + SSL termination |

### Deployment Checklist

1. **PostgreSQL** backups: daily automated dump to object storage
2. **SSL certificate**: Let's Encrypt via Certbot (auto-renews)
3. **Monitoring**: UptimeRobot (free) for uptime checks
4. **Log aggregation**: Docker logs piped to a local file, rotated weekly
5. **Secret management**: Environment variables via `.env` file
6. **Zero-downtime deploy**: pull new image, restart container with health checks

### Disaster Recovery

- Daily database backup (automated)
- If server dies: restore backup on a new VPS in ~15 minutes
- Acceptable downtime: 1–2 hours for a business of this size

---

## 19. Open-Source References

### Subscription & Billing Systems

| Project | Language | What to Study |
|---|---|---|
| [Subscribie](https://github.com/Subscribie/subscribie) | Python | Subscription state machine, billing logic |
| [Meteroid](https://github.com/meteroid-oss/meteroid) | Rust/TypeScript | Subscription lifecycle events |
| [Kill Bill](https://github.com/killbill/killbill) | Java | Most complete open-source billing — study for concepts, don't fork |

### Wallet / Ledger Systems

| Resource | What to Learn |
|---|---|
| [Modern Treasury — Ledger Design](https://www.moderntreasury.com/journal/accounting-for-developers-part-ii) | Double-entry bookkeeping for developers |
| [Pragmatic Engineer — Payment System](https://newsletter.pragmaticengineer.com/p/designing-a-payment-system) | Ledger + wallet service architecture |
| [TigerBeetle concepts](https://tigerbeetle.com) | Financial-grade database design principles |

### Scheduler Patterns

| Resource | What to Learn |
|---|---|
| [Google SRE Book — Cron](https://sre.google/sre-book/distributed-periodic-scheduling/) | Idempotency, skip vs double-run tradeoffs |
| [pg_cron](https://github.com/citusdata/pg_cron) | Database-native scheduling |

---

## Engineering Challenges Summary

| Challenge | How to Solve |
|---|---|
| Duplicate order generation | Idempotency key per (subscription_id, date) with unique constraint |
| Double balance deduction | Unique constraint on (order_id, source_type) in wallet_ledger |
| Deduction before delivery | Balance deduction triggered only on DELIVERED, in same transaction |
| Effective date correctness | Compute at API layer, store as subscription_change_requests record |
| Scheduler failure recovery | Database-backed job log; missed jobs auto-rerun on startup; admin can manually rerun |
| Race conditions in balance | Row-level locking on balance read before deduction |
| Historical audit | Append-only ledger + admin_audit_log; never update/delete financial records |
| Operational delivery grouping | Single hardcoded "Morning" delivery window used internally for delivery sheets and reporting |
| Duplicate subscriptions | Partial unique index on `(customer_id, product_id)` where `status IN ('ACTIVE', 'PAUSED', 'PENDING_START')` |
| Holiday handling | business_holidays table checked during order generation |
| Money precision | BIGINT paise everywhere; no floating point for financial calculations |
| Order state semantics | `CANCELLED` = pre-delivery business invalidation (pause/cancel/product-disable); `SKIPPED` = delivery-day operational skip; insufficient-balance orders are never inserted |
| Conflict resolution | Latest approved request of same type wins; earlier marked SUPERSEDED |
| Single delivery address | UNIQUE(customer_id) on delivery_addresses table |
| Session management | Refresh tokens in DB; new login revokes previous token |

---

## Additional Documents (Source of Truth for Implementation)

The following documents are maintained separately and serve as the AI-generation source of truth:

**`/docs/api-spec.md`** — All endpoints, request DTOs, response DTOs, auth rules, validation rules.

**`/docs/db-schema.md`** — Table schemas, indexes, constraints, foreign keys, enums.

**`/docs/business-rules.md`** — All operational rules, effective date logic, cutoff rules, scheduler rules, financial rules, conflict resolution rules.

---

*Document prepared for: Single-vendor juice recurring subscription delivery platform*
*Architecture tier: Production-ready, AI-executable backend specification*
*Initial scale: ~20 customers, ~30 deliveries/day, ~10–12 products, Sector 16B Noida Extension*
*Estimated MVP build time: 2–3 months for a focused solo developer*
