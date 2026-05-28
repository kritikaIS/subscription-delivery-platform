# Subscription Delivery Platform

Production-style Spring Boot backend for a subscription-based juice delivery platform with JWT authentication, scheduled order generation, wallet ledger accounting, delivery lifecycle management, transactional financial settlement, PostgreSQL, and Flyway migrations.

---

# Features

## Authentication & Authorization

* JWT-based authentication
* Refresh token workflow
* Role-based access control (ADMIN / CUSTOMER)
* Stateless security architecture

## Customer Onboarding

* Customer profile creation
* Delivery address management
* Google login integration support

## Product Management

* Admin product creation and updates
* Product enable/disable flows
* Product price history tracking

## Subscription System

* Create subscription
* Pause / resume subscription
* Cancel subscription
* Scheduled subscription activation

## Order Lifecycle

* Automated daily order generation
* Idempotent duplicate protection
* Order freezing scheduler
* Delivery record generation

## Wallet Ledger System

* Append-only wallet ledger architecture
* Admin wallet credit
* Delivery-based wallet debit
* Running balance computation
* Financial transaction consistency

## Delivery Execution

* Delivery confirmation
* Skipped delivery handling
* Delivery status tracking
* Transactional settlement workflow

## Scheduler Infrastructure

* SubscriptionActivationJob
* OrderGenerationJob
* OrderFreezeJob
* Scheduler job logging
* Idempotent rerun handling

---

# Tech Stack

* Java 21
* Spring Boot 3
* Spring Security
* Spring Data JPA
* PostgreSQL
* Flyway
* Maven
* JWT (JJWT)

---

# Architecture Highlights

* Layered architecture
* Stateless authentication
* Transactional financial workflows
* Append-only accounting ledger
* Scheduler-driven order orchestration
* Idempotent job execution
* PostgreSQL enum mapping
* Domain-driven workflow separation

---

# Database Design

Core tables:

* users
* subscriptions
* orders
* delivery_records
* wallet_ledger
* products
* product_price_history
* scheduler_job_log
* refresh_tokens

---

# Scheduler Workflows

## Subscription Activation

```text
PENDING_START → ACTIVE
```

## Order Generation

```text
ACTIVE subscription
→ SCHEDULED order
```

## Order Freeze

```text
SCHEDULED → LOCKED
+ delivery_record(PENDING)
```

## Delivery Settlement

```text
LOCKED
→ DELIVERED
→ wallet debit
→ ledger entry
```

---

# API Modules

* Auth APIs
* Customer APIs
* Product APIs
* Subscription APIs
* Wallet APIs
* Order APIs
* Admin APIs
* Scheduler Dev APIs

---

# Local Setup

## Prerequisites

* Java 21
* Maven
* Docker
* PostgreSQL

## Clone Repository

```bash
git clone https://github.com/kritikaIS/subscription-delivery-platform.git
cd subscription-delivery-platform
```

## Configure Environment Variables

```bash
export JWT_SECRET="your-secret-key"
export GOOGLE_CLIENT_ID="your-google-client-id"
```

## Start PostgreSQL

```bash
docker compose up -d
```

## Run Backend

```bash
cd backend
mvn spring-boot:run
```

---

# Future Improvements

* Notification system
* Delivery sheet generation
* Audit logging
* Integration testing
* Dockerized deployment
* CI/CD pipeline
* Monitoring & observability
* Kubernetes deployment

---

# Project Status

Backend MVP completed with:

* authentication
* subscriptions
* schedulers
* wallet ledger
* delivery lifecycle
* transactional settlement
* PostgreSQL persistence
* idempotent workflows
