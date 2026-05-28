# Juice Delivery Platform — API Specification

## Global Conventions

### Base URL
All endpoints are prefixed with `/api/v1`.

### Authentication
- **Customer endpoints** require a JWT access token in the `Authorization` header: `Bearer <token>`
- **Admin endpoints** require a JWT access token issued via admin login
- **Public endpoints** (customer/admin login and token refresh) require no token

#### 401 vs 403 Behavior

```
401 Unauthorized:
Missing, invalid, expired, or revoked JWT.

403 Forbidden:
Authenticated user lacks permission, onboarding is incomplete, or customer account is deactivated.
```

#### Onboarding Middleware Rule

All customer business APIs require `onboardingComplete=true` unless explicitly stated otherwise. Requests from customers with incomplete onboarding are rejected with `403 ONBOARDING_INCOMPLETE`.

### Field Naming
All JSON request and response field names use **camelCase**.

### Monetary Values
All monetary amounts are represented as **BIGINT paise** (e.g. ₹25.00 = `2500`). No decimal/float money values anywhere.

### Timestamps
All timestamps use ISO-8601 format with timezone offset. Example: `2025-07-15T10:30:00+05:30`

### Timezone
All dates, timestamps, scheduler operations, and cutoff calculations use **Asia/Kolkata** timezone unless explicitly stated otherwise.

### Pagination
List endpoints accept `page` (0-indexed) and `size` query params. Default: `page=0`, `size=20`. Maximum allowed page size is `100`.

### Sorting
List endpoints may support `sortBy=<field>` and `sortDirection=asc|desc` query params. Default sorting is newest-first where applicable.

### Resource IDs
All resource IDs use UUID format unless otherwise specified.

### Action Endpoints Without a Resource Response
Endpoints that perform actions without returning a specific resource still return a standard success envelope with relevant operation metadata. Example:

```json
{
  "success": true,
  "data": {
    "status": "COMPLETED"
  }
}
```

### Response Envelope
All responses are wrapped:

**Success (list/paginated):**
```json
{
  "success": true,
  "data": {
    "items": [...]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 45
  }
}
```

**Success (non-list):**
```json
{
  "success": true,
  "data": { ... }
}
```

For non-list responses, `meta` may be omitted or `{}`.

**Error:**
```json
{
  "success": false,
  "error": {
    "code": "SUBSCRIPTION_DUPLICATE",
    "message": "A subscription for this product already exists."
  }
}
```

### HTTP Status Codes
| Code | Meaning |
|---|---|
| 200 | Success |
| 201 | Created |
| 400 | Validation error or business rule violation |
| 401 | Missing, invalid, expired, or revoked JWT token |
| 403 | Authenticated user lacks permission OR onboarding is incomplete for customer business APIs |
| 404 | Resource not found |
| 409 | Conflict (duplicate, state violation) |
| 429 | Rate limit exceeded |
| 500 | Internal server error |

---

## Domain 1 — Authentication

### 1.1 Customer Google Login
`POST /api/v1/auth/customer/google`

Verifies the Google ID token server-side and issues a JWT access + refresh token pair. Creates the customer account if it does not exist.

**Auth:** None

**Request:**
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "dGhpcyBpcyBh...",
    "customerId": "a1b2c3d4-...",
    "onboardingComplete": false
  }
}
```

**Notes:**
- If `onboardingComplete` is `false`, the customer app must redirect to the onboarding flow before any business API is accessible.
- Any previously active session for this customer is immediately invalidated.

---

### 1.2 Customer Token Refresh
`POST /api/v1/auth/customer/refresh`

**Auth:** None

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBh..."
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "bmV3cmVmcmVzaA..."
  }
}
```

**Errors:**
- `401` — refresh token invalid, expired, or revoked (previous session)

---

### 1.3 Admin Login
`POST /api/v1/auth/admin/login`

**Auth:** None

**Request:**
```json
{
  "phone": "9876543210",
  "password": "securepassword"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "dGhpcyBpcyBh..."
  }
}
```

**Errors:**
- `401` — invalid phone or password

---

### 1.4 Admin Token Refresh
`POST /api/v1/auth/admin/refresh`

**Auth:** None

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBh..."
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "bmV3cmVmcmVzaA..."
  }
}
```

---

**Authentication Notes:**
- Customer authentication is handled exclusively through Google OAuth.
- Admin authentication uses phone/password login.
- Admin password reset is handled operationally during MVP deployment. Self-service admin password recovery is intentionally unsupported.

---

## Domain 2 — Onboarding (Customer)

### 2.1 Complete Onboarding
`POST /api/v1/onboarding`

Completes customer onboarding by providing phone number and delivery address. This endpoint is intended for **initial onboarding only**. If onboarding is already complete, this endpoint returns the existing onboarding data without modification. For updating the delivery address after onboarding, use `PUT /api/v1/customer/address`.

**Auth:** Customer JWT

**Request:**
```json
{
  "phone": "9876543210",
  "address": {
    "line1": "42 MG Road",
    "line2": "Apt 3B",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560001",
    "deliveryNotes": "Leave at the door"
  }
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "customerId": "a1b2c3d4-...",
    "phone": "9876543210",
    "onboardingComplete": true,
    "address": {
      "id": "addr-uuid",
      "line1": "42 MG Road",
      "line2": "Apt 3B",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560001",
      "deliveryNotes": "Leave at the door"
    }
  }
}
```

---

### 2.2 Update Delivery Address
`PUT /api/v1/customer/address`

Updates the customer's delivery address immediately. No cutoff rule applies. Future order generation uses the updated address.

**Auth:** Customer JWT

**Request:**
```json
{
  "line1": "10 Brigade Road",
  "line2": "",
  "city": "Bengaluru",
  "state": "Karnataka",
  "pincode": "560025",
  "deliveryNotes": "Ring the bell"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "addr-uuid-new",
    "line1": "10 Brigade Road",
    "line2": "",
    "city": "Bengaluru",
    "state": "Karnataka",
    "pincode": "560025",
    "deliveryNotes": "Ring the bell",
    "updatedAt": "2025-07-15T10:30:00+05:30"
  }
}
```

---

### 2.3 Get My Profile
`GET /api/v1/customer/me`

Returns the authenticated customer's profile, onboarding status, delivery address, and wallet summary. Designed for frontend bootstrapping and profile screens.

**Auth:** Customer JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-...",
    "name": "Ravi Kumar",
    "email": "ravi@example.com",
    "phone": "9876543210",
    "onboardingComplete": true,
    "address": {
      "id": "addr-uuid",
      "line1": "42 MG Road",
      "line2": "Apt 3B",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560001",
      "deliveryNotes": "Leave at the door"
    },
    "wallet": {
      "balancePaise": 45000,
      "lowBalanceWarning": false,
      "lowBalanceThresholdPaise": 20000
    },
    "createdAt": "2025-06-01T10:00:00+05:30"
  }
}
```

**Notes:**
- `address` is `null` if onboarding is not yet complete.
- `wallet` reflects the current live balance at time of request.

---

## Domain 3 — Products (Customer)

### 3.1 List Available Products
`GET /api/v1/products`

Returns all enabled products visible to customers.

**Auth:** Customer JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "prod-uuid-1",
        "name": "Orange Juice",
        "description": "Freshly squeezed oranges",
        "pricePerUnitPaise": 2500,
        "unitLabel": "500ml bottle",
        "imageUrl": "https://cdn.example.com/oj.jpg"
      },
      {
        "id": "prod-uuid-2",
        "name": "Watermelon Juice",
        "description": "Cold-pressed watermelon",
        "pricePerUnitPaise": 3000,
        "unitLabel": "500ml bottle",
        "imageUrl": "https://cdn.example.com/wm.jpg"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 2
  }
}
```

---

## Domain 4 — Subscriptions (Customer)

### 4.1 Create Subscription
`POST /api/v1/subscriptions`

Creates a new subscription. The effective start date is determined by the cutoff rule (BR-CUT-03 / BR-CUT-04).

**Auth:** Customer JWT (onboarding must be complete)

**Request:**
```json
{
  "productId": "prod-uuid-1",
  "quantity": 2
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "id": "sub-uuid-1",
    "productId": "prod-uuid-1",
    "productName": "Orange Juice",
    "quantity": 2,
    "status": "PENDING_START",
    "effectiveStartDate": "2025-07-16",
    "createdAt": "2025-07-15T09:00:00+05:30"
  }
}
```

**Errors:**
- `409` `SUBSCRIPTION_DUPLICATE` — active/paused/pending subscription for this product already exists
- `400` `PRODUCT_UNAVAILABLE` — product is disabled
- `403` `ONBOARDING_INCOMPLETE` — customer has not completed onboarding

---

### 4.2 List My Subscriptions
`GET /api/v1/subscriptions`

Returns all subscriptions for the authenticated customer.

**Auth:** Customer JWT

**Query params:** `page`, `size`, optional `status` filter (e.g. `?status=ACTIVE`)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "sub-uuid-1",
        "productId": "prod-uuid-1",
        "productName": "Orange Juice",
        "quantity": 2,
        "status": "ACTIVE",
        "effectiveStartDate": "2025-07-16",
        "createdAt": "2025-07-15T09:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

---

### 4.3 Get Subscription Detail
`GET /api/v1/subscriptions/{id}`

**Auth:** Customer JWT (must own the subscription)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "sub-uuid-1",
    "productId": "prod-uuid-1",
    "productName": "Orange Juice",
    "quantity": 2,
    "status": "ACTIVE",
    "effectiveStartDate": "2025-07-16",
    "pendingChangeRequests": [
      {
        "type": "QUANTITY",
        "newQuantity": 3,
        "effectiveDate": "2025-07-17",
        "status": "APPROVED"
      }
    ],
    "createdAt": "2025-07-15T09:00:00+05:30"
  }
}
```

**Errors:**
- `403` — subscription belongs to another customer

---

### 4.4 Change Quantity
`POST /api/v1/subscriptions/{id}/change-quantity`

Submits a quantity change request. Effective date follows the cutoff rule. Any existing APPROVED quantity change request for this subscription is superseded.

**Auth:** Customer JWT (must own the subscription)

**Request:**
```json
{
  "newQuantity": 3
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "changeRequestId": "cr-uuid-1",
    "type": "QUANTITY",
    "newQuantity": 3,
    "status": "APPROVED",
    "effectiveDate": "2025-07-16"
  }
}
```

**Errors:**
- `400` `INVALID_QUANTITY` — quantity must be ≥ 1
- `409` `SUBSCRIPTION_NOT_MODIFIABLE` — subscription is PENDING_START or CANCELLED

---

### 4.5 Change Product
`POST /api/v1/subscriptions/{id}/change-product`

Submits a product change request. Effective date follows the cutoff rule. Any existing APPROVED product change request for this subscription is superseded.

**Auth:** Customer JWT (must own the subscription)

**Request:**
```json
{
  "newProductId": "prod-uuid-2"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "changeRequestId": "cr-uuid-2",
    "type": "PRODUCT",
    "newProductId": "prod-uuid-2",
    "newProductName": "Watermelon Juice",
    "status": "APPROVED",
    "effectiveDate": "2025-07-16"
  }
}
```

**Errors:**
- `400` `PRODUCT_UNAVAILABLE` — target product is disabled
- `409` `SUBSCRIPTION_DUPLICATE` — customer already has an active subscription for the target product
- `409` `SUBSCRIPTION_NOT_MODIFIABLE` — subscription is PENDING_START or CANCELLED

---

### 4.6 Pause Subscription
`POST /api/v1/subscriptions/{id}/pause`

Pauses the subscription. Effective date follows the cutoff rule. Future SCHEDULED orders are set to CANCELLED. LOCKED orders are unaffected.

**Auth:** Customer JWT (must own the subscription)

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "status": "PAUSED",
    "pauseEffectiveDate": "2025-07-16"
  }
}
```

**Errors:**
- `409` `SUBSCRIPTION_ALREADY_PAUSED` — subscription is already PAUSED
- `409` `SUBSCRIPTION_NOT_PAUSABLE` — subscription is PENDING_START or CANCELLED

---

### 4.7 Resume Subscription
`POST /api/v1/subscriptions/{id}/resume`

Resumes a paused subscription. Effective date follows the cutoff rule. Delivery resumes from the next eligible date. Cancelled orders during the pause window are never regenerated.

**Auth:** Customer JWT (must own the subscription)

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "status": "ACTIVE",
    "resumeEffectiveDate": "2025-07-16"
  }
}
```

**Errors:**
- `409` `SUBSCRIPTION_NOT_PAUSED` — subscription is not currently PAUSED

---

### 4.8 Cancel Subscription
`POST /api/v1/subscriptions/{id}/cancel`

Cancels the subscription. LOCKED orders are unaffected. Future SCHEDULED orders are set to CANCELLED. Terminal state — cannot be reactivated.

**Auth:** Customer JWT (must own the subscription)

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "status": "CANCELLED",
    "cancelEffectiveDate": "2025-07-16",
    "cancelledAt": "2025-07-15T21:00:00+05:30"
  }
}
```

**Notes:**
- `cancelEffectiveDate` is computed by the cutoff rule (BR-CAN-02 / BR-CUT-03 / BR-CUT-04): if the request is before 22:00 IST, effective date is tomorrow; at or after 22:00 IST, effective date is day-after-tomorrow. `LOCKED` orders before the effective date are unaffected and will be fulfilled. Future `SCHEDULED` orders from the effective date onward are set to `CANCELLED`.

**Errors:**
- `409` `SUBSCRIPTION_ALREADY_CANCELLED` — subscription is already CANCELLED

---

## Domain 5 — Orders (Customer)

> **Business Rule:** Customers cannot modify orders directly. Order modifications occur only indirectly through subscription operations before the order lock time. LOCKED orders are immutable for customers.

> **Address Snapshot:** Orders contain an immutable delivery-address snapshot captured at order generation time. Future customer address changes do not modify already-generated orders.

### 5.1 List My Orders
`GET /api/v1/orders`

Returns all orders for the authenticated customer, newest first.

**Auth:** Customer JWT

**Query params:** `page`, `size`, optional `status` filter, optional `fromDate` / `toDate` (ISO date)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "order-uuid-1",
        "subscriptionId": "sub-uuid-1",
        "productName": "Orange Juice",
        "quantity": 2,
        "totalAmountPaise": 5000,
        "deliveryDate": "2025-07-16",
        "status": "SCHEDULED",
        "isLocked": false
      },
      {
        "id": "order-uuid-2",
        "subscriptionId": "sub-uuid-1",
        "productName": "Orange Juice",
        "quantity": 2,
        "totalAmountPaise": 5000,
        "deliveryDate": "2025-07-15",
        "status": "DELIVERED",
        "isLocked": true
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 42
  }
}
```

---

### 5.2 Get Order Detail
`GET /api/v1/orders/{id}`

**Auth:** Customer JWT (must own the order)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "order-uuid-1",
    "subscriptionId": "sub-uuid-1",
    "productId": "prod-uuid-1",
    "productName": "Orange Juice",
    "quantity": 2,
    "unitPricePaise": 2500,
    "totalAmountPaise": 5000,
    "deliveryDate": "2025-07-16",
    "deliveryAddress": {
      "line1": "42 MG Road",
      "line2": "Apt 3B",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560001",
      "deliveryNotes": "Leave at the door"
    },
    "status": "SCHEDULED",
    "isLocked": false,
    "createdAt": "2025-07-14T22:06:00+05:30"
  }
}
```

---

## Domain 6 — Wallet (Customer)

### 6.1 Get Wallet Summary
`GET /api/v1/wallet`

Returns current wallet balance and a summary.

**Auth:** Customer JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "balancePaise": 45000,
    "lowBalanceWarning": false,
    "lowBalanceThresholdPaise": 20000
  }
}
```

**Notes:**
- `lowBalanceWarning` is a convenience field computed server-side. It equals `balancePaise < lowBalanceThresholdPaise`.

---

### 6.2 Get Ledger History
`GET /api/v1/wallet/ledger`

Returns the full ledger history for the authenticated customer, newest first.

**Auth:** Customer JWT

**Query params:** `page`, `size`, optional `fromDate` (ISO date), optional `toDate` (ISO date), optional `entryType`, optional `sourceType`

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "ledger-uuid-1",
        "entryType": "DEBIT",
        "sourceType": "DELIVERY_DEBIT",
        "amountPaise": 5000,
        "balanceAfterPaise": 40000,
        "description": "Delivery on 2025-07-15 — Orange Juice x2",
        "createdAt": "2025-07-15T08:45:00+05:30"
      },
      {
        "id": "ledger-uuid-2",
        "entryType": "CREDIT",
        "sourceType": "ADMIN_CREDIT",
        "amountPaise": 50000,
        "balanceAfterPaise": 45000,
        "description": "Wallet top-up by admin",
        "createdAt": "2025-07-14T11:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 18
  }
}
```

---

### 6.3 Request Wallet Recharge
`POST /api/v1/wallet/recharge-request`

Sends a notification to admin alerting them to recharge the customer's wallet. Does not mutate balance. Audit logged.

**Auth:** Customer JWT

**Request:**
```json
{
  "notes": "Wallet balance low"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "status": "REQUESTED",
    "requestedAt": "2025-07-15T10:00:00+05:30"
  }
}
```

**Notes:**
- This endpoint is stateless. No wallet recharge request entity or table exists in MVP — the endpoint only triggers a notification to admin and an audit log entry. Balance is not modified.
- This endpoint should be rate-limited per customer (e.g. at most one request per hour) to prevent notification spam to the admin. Subsequent requests within the rate-limit window should return `429 Too Many Requests`.

---

## Domain 7 — Admin: Customers

### 7.1 List All Customers
`GET /api/v1/admin/customers`

**Auth:** Admin JWT

**Query params:** `page`, `size`, optional `search` (name/phone/email), optional `isActive` filter

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "a1b2c3d4-...",
        "name": "Ravi Kumar",
        "email": "ravi@example.com",
        "phone": "9876543210",
        "isActive": true,
        "onboardingComplete": true,
        "walletBalancePaise": 45000,
        "activeSubscriptionCount": 2,
        "createdAt": "2025-06-01T10:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 18
  }
}
```

---

### 7.2 Get Customer Detail
`GET /api/v1/admin/customers/{id}`

**Auth:** Admin JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-...",
    "name": "Ravi Kumar",
    "email": "ravi@example.com",
    "phone": "9876543210",
    "isActive": true,
    "onboardingComplete": true,
    "address": {
      "line1": "42 MG Road",
      "line2": "Apt 3B",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560001",
      "deliveryNotes": "Leave at the door"
    },
    "walletBalancePaise": 45000,
    "createdAt": "2025-06-01T10:00:00+05:30"
  }
}
```

---

### 7.3 Deactivate Customer
`POST /api/v1/admin/customers/{id}/deactivate`

Soft-deletes the customer. All data is retained. Customer can no longer access authenticated customer APIs. Existing sessions are rejected with `403 ACCOUNT_DEACTIVATED`.

**Auth:** Admin JWT

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "customerId": "a1b2c3d4-...",
    "isActive": false,
    "deactivatedAt": "2025-07-15T10:00:00+05:30"
  }
}
```

---

### 7.4 Reactivate Customer
`POST /api/v1/admin/customers/{id}/reactivate`

**Auth:** Admin JWT

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "customerId": "a1b2c3d4-...",
    "isActive": true
  }
}
```

---

## Domain 8 — Admin: Wallet

### 8.1 Credit Wallet
`POST /api/v1/admin/customers/{id}/wallet/credit`

Manually credits the customer's wallet after external payment. Inserts a CREDIT ledger entry. Minimum wallet credit amount is ₹1 (100 paise). Admin JWT required.

**Auth:** Admin JWT

**Request:**
```json
{
  "amountPaise": 50000,
  "notes": "Customer paid via UPI — ref TXN123456"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "ledgerEntryId": "ledger-uuid-3",
    "entryType": "CREDIT",
    "sourceType": "ADMIN_CREDIT",
    "amountPaise": 50000,
    "newBalancePaise": 95000,
    "notes": "Customer paid via UPI — ref TXN123456",
    "createdAt": "2025-07-15T11:00:00+05:30"
  }
}
```

**Errors:**
- `400` `INVALID_AMOUNT` — amountPaise must be ≥ 100 (equivalent to ₹1; BR-WAL-07)

---

### 8.2 Manual Ledger Adjustment
`POST /api/v1/admin/customers/{id}/wallet/adjust`

Inserts a manual `ADJUSTMENT`, `REFUND`, or `DEBIT` ledger entry for corrections. Cannot result in negative balance.

**Auth:** Admin JWT

**Request:**
```json
{
  "entryType": "REFUND",
  "amountPaise": 2500,
  "notes": "Refund for missed delivery on 2025-07-10"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "ledgerEntryId": "ledger-uuid-4",
    "entryType": "REFUND",
    "sourceType": "REFUND",
    "amountPaise": 2500,
    "newBalancePaise": 47500,
    "notes": "Refund for missed delivery on 2025-07-10",
    "createdAt": "2025-07-15T11:05:00+05:30"
  }
}
```

**`entryType` → `sourceType` mapping:**
| entryType | sourceType |
|---|---|
| `REFUND` | `REFUND` |
| `ADJUSTMENT` | `MANUAL_ADJUSTMENT` |
| `DEBIT` | `MANUAL_DEBIT` |

**Notes:**
- `REFUND` and `ADJUSTMENT` add to the balance. `DEBIT` (with sourceType `MANUAL_DEBIT`) deducts from the balance.
- All three are audit logged with `acting_admin`, `action_type`, and `notes`.

**Errors:**
- `400` `NEGATIVE_BALANCE` — adjustment or debit would result in negative balance
- `400` `INVALID_ENTRY_TYPE` — entryType must be one of: `REFUND`, `ADJUSTMENT`, `DEBIT`

---

### 8.3 Set Wallet Balance
`POST /api/v1/admin/customers/{id}/wallet/set-balance`

Operationally sets a customer's wallet balance to an exact amount. Implemented internally by inserting a `SYSTEM_ADJUSTMENT` ledger entry. Existing ledger rows are never modified or deleted.

**Auth:** Admin JWT

**Request:**
```json
{
  "newBalancePaise": 50000,
  "reason": "Operational correction"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "ledgerEntryId": "ledger-uuid-5",
    "entryType": "CREDIT",
    "sourceType": "SYSTEM_ADJUSTMENT",
    "newBalancePaise": 50000,
    "reason": "Operational correction",
    "createdAt": "2025-07-15T11:10:00+05:30"
  }
}
```

**Errors:**
- `400` `INVALID_AMOUNT` — newBalancePaise must be ≥ 0

**Notes:**
The backend computes the ledger delta against the customer's current wallet balance.

Rules:
- `amountPaise` stores the absolute delta amount
- `entryType` = `CREDIT` when balance increases
- `entryType` = `DEBIT` when balance decreases
- `sourceType` = `SYSTEM_ADJUSTMENT`

---

### 8.4 Get Customer Ledger (Admin View)
`GET /api/v1/admin/customers/{id}/wallet/ledger`

Full ledger history for a customer, newest first.

**Auth:** Admin JWT

**Query params:** `page`, `size`, optional `fromDate` (ISO date), optional `toDate` (ISO date), optional `entryType`, optional `sourceType`

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "ledger-uuid-1",
        "entryType": "DEBIT",
        "sourceType": "DELIVERY_DEBIT",
        "amountPaise": 5000,
        "balanceAfterPaise": 40000,
        "description": "Delivery on 2025-07-15 — Orange Juice x2",
        "orderId": "order-uuid-2",
        "createdAt": "2025-07-15T08:45:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 18
  }
}
```

---

## Domain 9 — Admin: Subscriptions

### 9.1 List All Subscriptions
`GET /api/v1/admin/subscriptions`

**Auth:** Admin JWT

**Query params:** `page`, `size`, `customerId` (filter), `status` (filter), `productId` (filter)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "sub-uuid-1",
        "customerId": "a1b2c3d4-...",
        "customerName": "Ravi Kumar",
        "productId": "prod-uuid-1",
        "productName": "Orange Juice",
        "quantity": 2,
        "status": "ACTIVE",
        "effectiveStartDate": "2025-07-01",
        "createdAt": "2025-06-30T09:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 22
  }
}
```

---

### 9.2 Admin Override Subscription
`PATCH /api/v1/admin/subscriptions/{id}`

Admin may override subscription status, quantity, or product directly. All changes are fully audit logged and support an optional internal admin notes field.

Admin subscription overrides directly mutate the subscription state.

They:
- do NOT create `subscription_change_requests` rows
- bypass normal effective-date scheduling
- apply immediately

**Auth:** Admin JWT

**Request:**
```json
{
  "status": "ACTIVE",
  "quantity": 3,
  "productId": "uuid-string",
  "notes": "Manual correction per customer phone call"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "sub-uuid-1",
    "status": "ACTIVE",
    "quantity": 3,
    "updatedAt": "2025-07-15T10:00:00+05:30"
  }
}
```

**Explicit State Transition Rules:**

**If admin sets subscription → `PAUSED`:**
- Cutoff rules apply (BR-CUT-03 / BR-CUT-04)
- Future `SCHEDULED` orders become `CANCELLED`
- Existing `LOCKED` orders remain unchanged

**If admin sets subscription → `CANCELLED`:**
- Terminal state — cannot be reactivated
- Future `SCHEDULED` orders become `CANCELLED`
- Existing `LOCKED` orders remain unchanged

**If admin sets subscription → `ACTIVE`:**
- Only allowed from `PAUSED`
- Cutoff rules apply (BR-CUT-03 / BR-CUT-04)
- Previously cancelled pause-window orders are NOT regenerated

**If admin changes `quantity` or `product`:**
- Affects future order generation only
- Existing `LOCKED` orders are never modified

**All admin overrides:**
- Fully audit logged
- Include optional internal `notes` field
- Bypass normal cutoff rules and apply immediately

**Errors:**
- `409` `INVALID_STATUS_TRANSITION` — e.g. attempting to set `CANCELLED` subscription to `ACTIVE`, or setting any subscription to `ACTIVE` from a non-`PAUSED` state

---

## Domain 9b — Change Request History

### 9b.1 List Change Requests (Customer)
`GET /api/v1/subscriptions/{id}/change-requests`

Returns the change request history for a subscription owned by the authenticated customer.

**Auth:** Customer JWT (must own the subscription)

**Query params:** `page`, `size`, optional `type` (`QUANTITY` | `PRODUCT`), optional `status` (`APPROVED` | `APPLIED` | `SUPERSEDED`)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "cr-uuid-1",
        "type": "QUANTITY",
        "newQuantity": 3,
        "newProductId": null,
        "newProductName": null,
        "status": "APPLIED",
        "effectiveDate": "2025-07-16",
        "requestedBy": "CUSTOMER",
        "createdAt": "2025-07-15T09:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 5
  }
}
```

**Errors:**
- `403` — subscription belongs to another customer

---

### 9b.2 List Change Requests (Admin)
`GET /api/v1/admin/subscriptions/{id}/change-requests`

Returns the full change request history for any subscription.

**Auth:** Admin JWT

**Query params:** `page`, `size`, optional `type` (`QUANTITY` | `PRODUCT`), optional `status` (`APPROVED` | `APPLIED` | `SUPERSEDED`)

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "cr-uuid-1",
        "type": "QUANTITY",
        "newQuantity": 3,
        "newProductId": null,
        "newProductName": null,
        "status": "APPLIED",
        "effectiveDate": "2025-07-16",
        "requestedBy": "CUSTOMER",
        "requestedByAdminId": null,
        "createdAt": "2025-07-15T09:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 5
  }
}
```

**Notes:**
- `requestedBy` is `"CUSTOMER"` or `"ADMIN"`.
- `requestedByAdminId` is populated (UUID) when `requestedBy` is `"ADMIN"`; `null` otherwise.

---

## Domain 10 — Admin: Orders

> **Delivery Record Lifecycle:** During `OrderFreezeJob`, every order transitioning from `SCHEDULED` → `LOCKED` receives a `delivery_record` row initialized with `status = PENDING`. During delivery operations, this existing row is later updated to `DELIVERED` or `SKIPPED`. Orders cancelled before reaching LOCKED state never create delivery records.
>
> If a previously LOCKED order is historically corrected to CANCELLED, the existing delivery_record row is retained and its status transitions to CANCELLED.

### 10.1 List All Orders
`GET /api/v1/admin/orders`

**Auth:** Admin JWT

**Query params:** `page`, `size`, `customerId`, `status`, `deliveryDate`, `fromDate`, `toDate`

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "order-uuid-1",
        "customerId": "a1b2c3d4-...",
        "customerName": "Ravi Kumar",
        "productName": "Orange Juice",
        "quantity": 2,
        "totalAmountPaise": 5000,
        "deliveryDate": "2025-07-16",
        "status": "LOCKED",
        "isLocked": true
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 30
  }
}
```

---

### 10.2 Get Order Detail (Admin)
`GET /api/v1/admin/orders/{id}`

**Auth:** Admin JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "order-uuid-1",
    "customerId": "a1b2c3d4-...",
    "customerName": "Ravi Kumar",
    "subscriptionId": "sub-uuid-1",
    "productId": "prod-uuid-1",
    "productName": "Orange Juice",
    "quantity": 2,
    "unitPricePaise": 2500,
    "totalAmountPaise": 5000,
    "deliveryDate": "2025-07-16",
    "status": "LOCKED",
    "isLocked": true,
    "cancellationComment": null,
    "deliveryAddress": {
      "line1": "42 MG Road",
      "line2": "Apt 3B",
      "city": "Bengaluru",
      "state": "Karnataka",
      "pincode": "560001",
      "deliveryNotes": "Leave at the door"
    },
    "createdAt": "2025-07-14T22:06:00+05:30"
  }
}
```

---

### 10.3 Mark Order Delivered
`POST /api/v1/admin/orders/{id}/deliver`

Marks a LOCKED order as DELIVERED. Wallet deduction and ledger entry are inserted in the same DB transaction. All changes are audit logged.

Repeated requests for an already DELIVERED order are treated as idempotent success.

The API returns HTTP 200 with the existing delivery state.
No additional wallet deduction or ledger entry occurs.

**Auth:** Admin JWT

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-1",
    "status": "DELIVERED",
    "amountDeductedPaise": 5000,
    "newWalletBalancePaise": 40000,
    "deliveredAt": "2025-07-16T08:30:00+05:30"
  }
}
```

**Errors:**
- `409` `ORDER_NOT_DELIVERABLE` — order is not in LOCKED state
- `400` `INSUFFICIENT_BALANCE` — wallet balance is less than order amount (this indicates an unexpected operational inconsistency and should not occur during normal scheduler flow)

**Idempotency:** Repeated requests for already `DELIVERED` orders must be idempotent and must not perform duplicate wallet deductions.

---

### 10.4 Mark Order Skipped
`POST /api/v1/admin/orders/{id}/skip`

Marks a LOCKED order as SKIPPED. No wallet deduction. Audit logged.

**Auth:** Admin JWT

**Request:**
```json
{
  "skipReason": "CUSTOMER_UNAVAILABLE"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-1",
    "status": "SKIPPED",
    "skipReason": "CUSTOMER_UNAVAILABLE",
    "skippedAt": "2025-07-16T08:35:00+05:30"
  }
}
```

**Idempotency:** Repeated requests for already `SKIPPED` orders must be idempotent and must not create duplicate delivery state transitions.

**Errors:**
- `400` `INVALID_SKIP_REASON` — skipReason must be one of: `CUSTOMER_UNAVAILABLE`, `PRODUCT_UNAVAILABLE`, `DAMAGED`, `OTHER`
- `409` `ORDER_NOT_SKIPPABLE` — order is not in LOCKED state

---

### 10.5 Override Order (Historical Correction)
`PATCH /api/v1/admin/orders/{id}`

Admin may edit a historical order's status or add a cancellation comment. If status changes from DELIVERED → SKIPPED and `isSystemError = true`, wallet is automatically refunded. Otherwise no automatic balance change. All edits are audit logged with before/after snapshots.

**Auth:** Admin JWT

**Request:**
```json
{
  "status": "SKIPPED",
  "skipReason": "DAMAGED",
  "isSystemError": false,
  "notes": "Admin correction on customer request",
  "quantity": 2,
  "productId": "uuid",
  "deliveryAddress": {
    "line1": "...",
    "line2": "...",
    "city": "...",
    "state": "...",
    "pincode": "...",
    "deliveryNotes": "..."
  },
  "cancellationComment": "Customer unavailable"
}
```

> **Note:** `quantity` and `deliveryAddress` overrides are allowed only for eligible `LOCKED` orders before delivery completion. Any quantity or product override immediately recalculates `unitPricePaise` and `totalAmountPaise` before persistence. Quantity-only overrides reuse the existing historical unitPricePaise already stored on the order. Product changes use the current product price at override time.

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-1",
    "status": "SKIPPED",
    "autoRefundIssued": false,
    "updatedAt": "2025-07-16T10:00:00+05:30"
  }
}
```

**Notes:**
- `autoRefundIssued: true` only when `isSystemError: true` AND previous status was `DELIVERED`. In this case a `REFUND` ledger entry (sourceType `HISTORICAL_CORRECTION`) is inserted atomically in the same DB transaction.
- `DELIVERED` → `SKIPPED` with `isSystemError: true` automatically inserts a `REFUND` ledger entry.
- `SKIPPED` → `DELIVERED` corrections automatically insert a standard `DELIVERY_DEBIT` ledger entry.
- All other historical corrections require separate manual ledger adjustments.
- `cancellationComment` is only valid and stored when the new `status` is `CANCELLED` (BR-LCK-03). Do not send `cancellationComment` for `SKIPPED` corrections — use the `notes` field instead, which is captured in the audit log.
- `skipReason` is only valid when the new `status` is `SKIPPED`.
- `isSystemError` is only evaluated when the status transition is `DELIVERED → SKIPPED`. In all other transitions it is ignored.
- Allowed historical corrections include:
  - `DELIVERED` → `SKIPPED`
  - `SKIPPED` → `DELIVERED`
  - `LOCKED` → `CANCELLED`
- Invalid transitions must return `409 INVALID_STATUS_TRANSITION`.
- Historical corrections must preserve wallet and delivery consistency.
- If a previously LOCKED order is historically corrected to CANCELLED, the existing delivery_record row is retained and its status transitions to CANCELLED. CANCELLED delivery records are excluded from delivery sheets and delivery operations.

---

## Domain 11 — Admin: Products

### 11.1 List All Products (Admin)
`GET /api/v1/admin/products`

Returns all products including disabled ones.

**Auth:** Admin JWT

**Query params:** `page`, `size`, optional `isAvailable` filter

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "prod-uuid-1",
        "name": "Orange Juice",
        "description": "Freshly squeezed oranges",
        "pricePerUnitPaise": 2500,
        "unitLabel": "500ml bottle",
        "isAvailable": true,
        "imageUrl": "https://cdn.example.com/oj.jpg",
        "createdAt": "2025-06-01T10:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 10
  }
}
```

---

### 11.2 Create Product
`POST /api/v1/admin/products`

**Auth:** Admin JWT

**Request:**
```json
{
  "name": "Pineapple Juice",
  "description": "Freshly pressed pineapple",
  "pricePerUnitPaise": 3500,
  "unitLabel": "500ml bottle",
  "imageUrl": "https://cdn.example.com/pj.jpg"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "id": "prod-uuid-3",
    "name": "Pineapple Juice",
    "description": "Freshly pressed pineapple",
    "pricePerUnitPaise": 3500,
    "unitLabel": "500ml bottle",
    "isAvailable": true,
    "imageUrl": "https://cdn.example.com/pj.jpg",
    "createdAt": "2025-07-15T10:00:00+05:30"
  }
}
```

---

### 11.3 Update Product
`PUT /api/v1/admin/products/{id}`

**Auth:** Admin JWT

**Request:**
```json
{
  "name": "Pineapple Juice",
  "description": "Updated description",
  "pricePerUnitPaise": 3800,
  "unitLabel": "500ml bottle",
  "imageUrl": "https://cdn.example.com/pj2.jpg"
}
```

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "prod-uuid-3",
    "name": "Pineapple Juice",
    "pricePerUnitPaise": 3800,
    "updatedAt": "2025-07-15T11:00:00+05:30"
  }
}
```

**Notes:**
- Price changes only affect future order generation. Existing orders retain the price at time of generation.
- `isAvailable` is not accepted in this endpoint. Product availability changes must use `/enable` or `/disable`. Any `isAvailable` field in the request body must be rejected with `INVALID_FIELD` or ignored.
- If `pricePerUnitPaise` changes, the backend automatically inserts a `product_price_history` row inside the same transaction.

---

### 11.4 Disable Product
`POST /api/v1/admin/products/{id}/disable`

Disables the product. All ACTIVE and PENDING_START subscriptions for this product are automatically transitioned to PAUSED. Both admin and affected customers are notified of the auto-pause.

**Auth:** Admin JWT

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "productId": "prod-uuid-1",
    "isAvailable": false,
    "autoPausedSubscriptionCount": 3,
    "disabledAt": "2025-07-15T10:00:00+05:30"
  }
}
```

**Notes:**
- Disabling a product does not cancel existing LOCKED or SCHEDULED orders.
- It only prevents future order generation.

---

### 11.5 Enable Product
`POST /api/v1/admin/products/{id}/enable`

Re-enables the product. Previously auto-paused subscriptions are NOT automatically resumed — admin or customer must manually resume each.

**Auth:** Admin JWT

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "productId": "prod-uuid-1",
    "isAvailable": true,
    "enabledAt": "2025-07-15T12:00:00+05:30"
  }
}
```

---

## Domain 12 — Admin: Holidays

### 12.1 List Holidays
`GET /api/v1/admin/holidays`

**Auth:** Admin JWT

**Query params:** `page`, `size`, optional `year` filter

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "holiday-uuid-1",
        "date": "2025-08-15",
        "name": "Independence Day",
        "createdAt": "2025-07-01T10:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 12
  }
}
```

---

### 12.2 Add Holiday
`POST /api/v1/admin/holidays`

**Auth:** Admin JWT

**Request:**
```json
{
  "date": "2025-10-02",
  "name": "Gandhi Jayanti"
}
```

**Response `201`:**
```json
{
  "success": true,
  "data": {
    "id": "holiday-uuid-2",
    "date": "2025-10-02",
    "name": "Gandhi Jayanti",
    "createdAt": "2025-07-15T10:00:00+05:30"
  }
}
```

**Errors:**
- `409` `HOLIDAY_ALREADY_EXISTS` — a holiday is already configured for this date

---

### 12.3 Delete Holiday
`DELETE /api/v1/admin/holidays/{id}`

Past or current business holidays cannot be deleted.

Only future-dated holidays may be deleted.

Attempting to delete a historical/current holiday returns:
400 HOLIDAY_IMMUTABLE

Permanently deletes the holiday record.

**Auth:** Admin JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "id": "holiday-uuid-2",
    "deleted": true
  }
}
```

**Notes:**
- Deleting a holiday that falls within an already-generated order window has no effect on existing orders.

---

## Domain 13 — Admin: Delivery Sheets

### 13.1 Get Delivery Sheet
`GET /api/v1/admin/delivery-sheets/{date}`

Returns the delivery sheet snapshot for a given date. Date format: `YYYY-MM-DD`.

**Auth:** Admin JWT

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "deliveryDate": "2025-07-16",
    "generatedAt": "2025-07-15T22:11:00+05:30",
    "orders": [
      {
        "orderId": "order-uuid-1",
        "customerName": "Ravi Kumar",
        "phone": "9876543210",
        "address": "42 MG Road, Apt 3B, Bengaluru 560001",
        "deliveryNotes": "Leave at the door",
        "productName": "Orange Juice",
        "quantity": 2
      }
    ],
    "juiceSummary": [
      {
        "productName": "Orange Juice",
        "totalQuantity": 18
      },
      {
        "productName": "Watermelon Juice",
        "totalQuantity": 12
      }
    ]
  }
}
```

**Notes:**
- This endpoint is admin-only.
- The `address` field in each order object is a display-formatted string assembled from the structured address fields (`line1`, `line2`, `city`, `pincode`) for operational readability on the delivery sheet. It is not stored separately — the backend concatenates these fields at response time.
- Delivery sheet responses are generated from delivery_sheet_snapshots. If admin corrections occur after snapshot generation, admins must manually rerun DeliverySheetGenerationJob to refresh the snapshot.

**Errors:**
- `404` — no delivery sheet exists for this date (not yet generated)

---

### 13.2 Download Delivery Sheet PDF
`GET /api/v1/admin/delivery-sheets/{date}/download/pdf`

**Auth:** Admin JWT

**Response:** `application/pdf` binary stream. Includes `Content-Disposition: attachment; filename="delivery-sheet-<date>.pdf"` header.

---

### 13.3 Download Delivery Sheet CSV
`GET /api/v1/admin/delivery-sheets/{date}/download/csv`

**Auth:** Admin JWT

**Response:** `text/csv` binary stream. Includes `Content-Disposition: attachment; filename="delivery-sheet-<date>.csv"` header.

---

## Domain 14 — Admin: Scheduler

### 14.1 Rerun OrderFreezeJob
`POST /api/v1/admin/scheduler/freeze`

Manually reruns the `OrderFreezeJob`. Idempotent.

**Auth:** Admin JWT

**Request:** _(optional)_
```json
{
  "targetDate": "2025-07-16"
}
```

> **Note:** If `targetDate` is omitted, it defaults to the next operational delivery date.

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "job": "OrderFreezeJob",
    "status": "COMPLETED",
    "targetDate": "2025-07-16",
    "ordersLocked": 28,
    "ranAt": "2025-07-15T22:00:00+05:30"
  }
}
```

---

### 14.2 Rerun OrderGenerationJob
`POST /api/v1/admin/scheduler/generate`

Manually reruns the `OrderGenerationJob`. Idempotent — already-generated orders are not duplicated. Use after crediting a previously blocked customer's wallet.

**Auth:** Admin JWT

**Request:** _(optional)_
```json
{
  "targetDate": "2025-07-16"
}
```

> **Note:** If `targetDate` is omitted, it defaults to the next operational delivery date.

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "job": "OrderGenerationJob",
    "status": "COMPLETED",
    "targetDate": "2025-07-16",
    "ordersGenerated": 2,
    "subscriptionsActivated": 0,
    "changeRequestsApplied": 1,
    "ranAt": "2025-07-15T22:05:00+05:30"
  }
}
```

---

### 14.3 Rerun DeliverySheetGenerationJob
`POST /api/v1/admin/scheduler/delivery-sheet`

**Auth:** Admin JWT

**Request:** _(optional)_
```json
{
  "targetDate": "2025-07-16"
}
```

> **Note:** If `targetDate` is omitted, it defaults to the next operational delivery date.

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "job": "DeliverySheetGenerationJob",
    "status": "COMPLETED",
    "targetDate": "2025-07-16",
    "ranAt": "2025-07-15T22:10:30+05:30"
  }
}
```

---

### 14.4 Get Scheduler Job History
`GET /api/v1/admin/scheduler/history`

**Auth:** Admin JWT

**Query params:** `page`, `size`, optional `jobName` filter

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "job-run-uuid-1",
        "jobName": "OrderGenerationJob",
        "status": "COMPLETED",
        "targetDate": "2025-07-16",
        "ordersGenerated": 28,
        "errorMessage": null,
        "ranAt": "2025-07-15T22:05:00+05:30"
      },
      {
        "id": "job-run-uuid-2",
        "jobName": "OrderFreezeJob",
        "status": "FAILED",
        "targetDate": "2025-07-15",
        "errorMessage": "DB connection timeout",
        "ranAt": "2025-07-14T22:00:00+05:30"
      }
    ]
  },
  "meta": {
    "page": 0,
    "size": 20,
    "total": 85
  }
}
```

---

## Domain 15 — Admin: Delivery Confirmation

### 15.1 Trigger Delivery Confirmation Email
`POST /api/v1/admin/orders/{id}/confirm-email`

Manually triggers the delivery confirmation email to the customer. Only valid for DELIVERED orders.

**Auth:** Admin JWT

**Request:** _(no body)_

**Response `200`:**
```json
{
  "success": true,
  "data": {
    "orderId": "order-uuid-1",
    "emailSentTo": "ravi@example.com",
    "sentAt": "2025-07-16T09:00:00+05:30"
  }
}
```

**Errors:**
- `409` `ORDER_NOT_DELIVERED` — order status is not DELIVERED

---

---

## Domain 16 — Delivery Slot

A single system-defined delivery slot exists internally for operational grouping and delivery sheet generation. Delivery slots are not customer-visible and no slot-management APIs are exposed in MVP.

---

## Appendix — Standard Enums

### Subscription Status
| Value | Description |
|---|---|
| `PENDING_START` | Created, awaiting first delivery date |
| `ACTIVE` | Actively generating orders |
| `PAUSED` | Paused by customer or admin |
| `CANCELLED` | Permanently cancelled; terminal state |

### Order Status
| Value | Description |
|---|---|
| `SCHEDULED` | Generated, not yet locked |
| `LOCKED` | Frozen for next-day delivery |
| `DELIVERED` | Delivered and wallet debited |
| `SKIPPED` | Skipped; no wallet deduction |
| `CANCELLED` | Cancelled (e.g. subscription paused/cancelled) |

> **Note:** Orders cancelled before reaching LOCKED state never create delivery records and never appear in delivery sheets.

### Ledger Entry Types
| Value | Description |
|---|---|
| `CREDIT` | Funds added to wallet |
| `DEBIT` | Funds deducted from wallet |
| `REFUND` | Refund issued to wallet |
| `ADJUSTMENT` | Manual correction by admin |

### Ledger Source Types
| Value | Description |
|---|---|
| `ADMIN_CREDIT` | Manual credit by admin |
| `DELIVERY_DEBIT` | Automatic deduction on delivery confirmation |
| `REFUND` | Admin-issued reversal |
| `MANUAL_DEBIT` | Admin-issued manual deduction |
| `MANUAL_ADJUSTMENT` | Manual correction by admin |
| `HISTORICAL_CORRECTION` | Admin corrects a historical record |
| `SYSTEM_ADJUSTMENT` | System-generated entry (no acting admin) |

### Skip Reasons
| Value | Description |
|---|---|
| `CUSTOMER_UNAVAILABLE` | Customer not available at delivery |
| `PRODUCT_UNAVAILABLE` | Product not available for delivery |
| `DAMAGED` | Product was damaged |
| `OTHER` | Other reason |

### Subscription Change Request Types
| Value | Description |
|---|---|
| `QUANTITY` | Quantity modification |
| `PRODUCT` | Product replacement |

### Pause Reasons
| Value | Description |
|---|---|
| `USER_PAUSED` | Paused by customer |
| `SYSTEM_PAUSED_PRODUCT_DISABLED` | Auto-paused due to product being disabled |
| `CUSTOMER_DEACTIVATED` | Paused due to customer account deactivation |

### Scheduler Job Statuses
| Value | Description |
|---|---|
| `RUNNING` | Job is currently in progress |
| `COMPLETED` | Job ran successfully |
| `FAILED` | Job encountered an error |

> Scheduler jobs persist `RUNNING` status immediately upon starting, then transition atomically to `COMPLETED` or `FAILED` once execution finishes. This allows the idempotency guard to detect and skip a job that is already in progress (or was abandoned mid-run due to a crash).

---

## Appendix — Scheduler Timings

All times are in **Asia/Kolkata** timezone.

| Job | Schedule |
|---|---|
| `OrderGenerationJob` | 22:05 IST daily |
| `OrderFreezeJob` | 22:00 IST daily |
| `DeliverySheetGenerationJob` | 22:10 IST daily |

---

## Appendix — Notification Events

All notifications are **email-only, best-effort, and non-blocking**. A notification failure is logged internally and never affects any business operation.

### Customer Notifications

| Trigger | Notes |
|---|---|
| Low balance warning | Fired during `OrderGenerationJob` when wallet balance < ₹200 (20,000 paise). Informational only — deliveries are not blocked. |
| Order generation blocked | Fired during `OrderGenerationJob` when wallet balance is insufficient to cover the upcoming order cost. |
| Delivery confirmation | **Manually triggered by admin** after marking an order delivered. Never sent automatically. |
| Subscription cancelled | Fired when a subscription transitions to `CANCELLED`. |
| Wallet credited | Fired when admin manually credits the customer's wallet. |
| Subscription auto-paused | Fired when a subscription is auto-paused due to its product being disabled. |

### Admin Notifications

| Trigger | Notes |
|---|---|
| Customer low balance | Fired during `OrderGenerationJob` when any customer's balance < ₹200 (20,000 paise). |
| Customer order generation blocked | Fired during `OrderGenerationJob` when any customer's balance is insufficient to generate their order. |
| Product auto-pause event | Fired when a product is disabled and subscriptions are auto-paused. |
| Scheduler job failure | Fired when any scheduled job completes with status `FAILED`. |

---

## Appendix — API Versioning Strategy

All endpoints are prefixed with `/api/v1`. The versioning strategy follows these rules:

- **Non-breaking changes** (new optional fields, new endpoints, new enum values) may be introduced in the current version without a version bump.
- **Breaking changes** (removed fields, changed field types, altered response structure, removed endpoints) require a new version prefix (e.g. `/api/v2`).
- Both versions are supported concurrently for a documented deprecation window before the older version is retired.
- Version negotiation is via URL path only; `Accept` header versioning is not used.
