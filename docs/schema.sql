--
-- PostgreSQL database dump
--

\restrict NO8NHYVF3oIi6bVmiOuUZzAw2YrOIN62k7xUBpX99i6VofgKK9t6sowTKtJMk4d

-- Dumped from database version 15.18 (Debian 15.18-1.pgdg13+1)
-- Dumped by pg_dump version 15.18 (Debian 15.18-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: auth_provider; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.auth_provider AS ENUM (
    'GOOGLE',
    'ADMIN_PASSWORD'
);


ALTER TYPE public.auth_provider OWNER TO juice_user;

--
-- Name: change_request_actor_type; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.change_request_actor_type AS ENUM (
    'CUSTOMER',
    'ADMIN'
);


ALTER TYPE public.change_request_actor_type OWNER TO juice_user;

--
-- Name: change_request_status; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.change_request_status AS ENUM (
    'APPROVED',
    'APPLIED',
    'SUPERSEDED'
);


ALTER TYPE public.change_request_status OWNER TO juice_user;

--
-- Name: change_request_type; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.change_request_type AS ENUM (
    'QUANTITY',
    'PRODUCT'
);


ALTER TYPE public.change_request_type OWNER TO juice_user;

--
-- Name: delivery_record_status; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.delivery_record_status AS ENUM (
    'PENDING',
    'DELIVERED',
    'SKIPPED',
    'CANCELLED'
);


ALTER TYPE public.delivery_record_status OWNER TO juice_user;

--
-- Name: delivery_sheet_source; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.delivery_sheet_source AS ENUM (
    'SCHEDULER',
    'ADMIN_RERUN'
);


ALTER TYPE public.delivery_sheet_source OWNER TO juice_user;

--
-- Name: order_status; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.order_status AS ENUM (
    'SCHEDULED',
    'LOCKED',
    'DELIVERED',
    'SKIPPED',
    'CANCELLED'
);


ALTER TYPE public.order_status OWNER TO juice_user;

--
-- Name: pause_reason; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.pause_reason AS ENUM (
    'USER_PAUSED',
    'SYSTEM_PAUSED_PRODUCT_DISABLED',
    'CUSTOMER_DEACTIVATED'
);


ALTER TYPE public.pause_reason OWNER TO juice_user;

--
-- Name: scheduler_job_status; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.scheduler_job_status AS ENUM (
    'RUNNING',
    'COMPLETED',
    'FAILED'
);


ALTER TYPE public.scheduler_job_status OWNER TO juice_user;

--
-- Name: skip_reason; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.skip_reason AS ENUM (
    'CUSTOMER_UNAVAILABLE',
    'PRODUCT_UNAVAILABLE',
    'DAMAGED',
    'OTHER'
);


ALTER TYPE public.skip_reason OWNER TO juice_user;

--
-- Name: subscription_status; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.subscription_status AS ENUM (
    'PENDING_START',
    'ACTIVE',
    'PAUSED',
    'CANCELLED'
);


ALTER TYPE public.subscription_status OWNER TO juice_user;

--
-- Name: user_role; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.user_role AS ENUM (
    'ADMIN',
    'CUSTOMER'
);


ALTER TYPE public.user_role OWNER TO juice_user;

--
-- Name: wallet_entry_type; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.wallet_entry_type AS ENUM (
    'CREDIT',
    'DEBIT',
    'REFUND'
);


ALTER TYPE public.wallet_entry_type OWNER TO juice_user;

--
-- Name: wallet_source_type; Type: TYPE; Schema: public; Owner: juice_user
--

CREATE TYPE public.wallet_source_type AS ENUM (
    'ADMIN_CREDIT',
    'DELIVERY_DEBIT',
    'REFUND',
    'MANUAL_DEBIT',
    'MANUAL_ADJUSTMENT',
    'HISTORICAL_CORRECTION',
    'SYSTEM_ADJUSTMENT',
    'HISTORICAL_CORRECTION_DEBIT'
);


ALTER TYPE public.wallet_source_type OWNER TO juice_user;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin_audit_log; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.admin_audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    action_type character varying(100) NOT NULL,
    target_entity character varying(50) NOT NULL,
    target_id character varying(255) NOT NULL,
    old_value jsonb,
    new_value jsonb,
    acting_admin uuid NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.admin_audit_log OWNER TO juice_user;

--
-- Name: admin_credentials; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.admin_credentials (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    password_hash character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.admin_credentials OWNER TO juice_user;

--
-- Name: business_holidays; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.business_holidays (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    holiday_date date NOT NULL,
    name character varying(100) NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.business_holidays OWNER TO juice_user;

--
-- Name: delivery_addresses; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.delivery_addresses (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    line1 character varying(255) NOT NULL,
    line2 character varying(255),
    city character varying(100) NOT NULL,
    state character varying(100) NOT NULL,
    pincode character varying(10) NOT NULL,
    delivery_notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.delivery_addresses OWNER TO juice_user;

--
-- Name: delivery_records; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.delivery_records (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    order_id uuid NOT NULL,
    delivery_date date NOT NULL,
    delivery_window character varying(50) DEFAULT 'Morning'::character varying NOT NULL,
    status public.delivery_record_status DEFAULT 'PENDING'::public.delivery_record_status NOT NULL,
    skip_reason public.skip_reason,
    delivered_at timestamp with time zone,
    notes text,
    photo_proof_url text,
    CONSTRAINT chk_delivery_records_delivered_at CHECK ((((status = 'DELIVERED'::public.delivery_record_status) AND (delivered_at IS NOT NULL)) OR ((status <> 'DELIVERED'::public.delivery_record_status) AND (delivered_at IS NULL)))),
    CONSTRAINT chk_delivery_records_delivery_window CHECK (((delivery_window)::text = 'Morning'::text)),
    CONSTRAINT chk_delivery_records_skip_reason CHECK ((((status = 'SKIPPED'::public.delivery_record_status) AND (skip_reason IS NOT NULL)) OR ((status <> 'SKIPPED'::public.delivery_record_status) AND (skip_reason IS NULL))))
);


ALTER TABLE public.delivery_records OWNER TO juice_user;

--
-- Name: delivery_sheet_snapshots; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.delivery_sheet_snapshots (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    delivery_date date NOT NULL,
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    generated_by_source public.delivery_sheet_source NOT NULL,
    generated_by_user_id uuid,
    snapshot_json jsonb NOT NULL
);


ALTER TABLE public.delivery_sheet_snapshots OWNER TO juice_user;

--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO juice_user;

--
-- Name: orders; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.orders (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    subscription_id uuid NOT NULL,
    product_id uuid NOT NULL,
    delivery_line1 character varying(255) NOT NULL,
    delivery_line2 character varying(255),
    delivery_city character varying(100) NOT NULL,
    delivery_state character varying(100) NOT NULL,
    delivery_pincode character varying(10) NOT NULL,
    delivery_notes text,
    delivery_date date NOT NULL,
    quantity integer NOT NULL,
    unit_price_paise bigint NOT NULL,
    total_amount_paise bigint NOT NULL,
    status public.order_status DEFAULT 'SCHEDULED'::public.order_status NOT NULL,
    skip_reason public.skip_reason,
    cancellation_comment text,
    cancellation_commented_by uuid,
    cancellation_commented_at timestamp with time zone,
    idempotency_key character varying(100) NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_orders_cancellation_comment_consistency CHECK (((cancellation_commented_by IS NULL) = (cancellation_commented_at IS NULL))),
    CONSTRAINT chk_orders_quantity_positive CHECK ((quantity >= 1)),
    CONSTRAINT chk_orders_skip_reason CHECK ((((status = 'SKIPPED'::public.order_status) AND (skip_reason IS NOT NULL)) OR ((status <> 'SKIPPED'::public.order_status) AND (skip_reason IS NULL)))),
    CONSTRAINT chk_orders_total_positive CHECK ((total_amount_paise > 0)),
    CONSTRAINT chk_orders_unit_price_positive CHECK ((unit_price_paise > 0))
);


ALTER TABLE public.orders OWNER TO juice_user;

--
-- Name: product_price_history; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.product_price_history (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    product_id uuid NOT NULL,
    old_price_paise bigint NOT NULL,
    new_price_paise bigint NOT NULL,
    changed_by uuid NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_product_price_history_new_positive CHECK ((new_price_paise > 0)),
    CONSTRAINT chk_product_price_history_old_positive CHECK ((old_price_paise > 0))
);


ALTER TABLE public.product_price_history OWNER TO juice_user;

--
-- Name: products; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.products (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    price_per_unit_paise bigint NOT NULL,
    unit_label character varying(20),
    category character varying(50),
    is_available boolean DEFAULT true NOT NULL,
    image_url text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_products_price_positive CHECK ((price_per_unit_paise > 0))
);


ALTER TABLE public.products OWNER TO juice_user;

--
-- Name: recharge_request_log; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.recharge_request_log (
    customer_id uuid NOT NULL,
    last_requested_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.recharge_request_log OWNER TO juice_user;

--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.refresh_tokens OWNER TO juice_user;

--
-- Name: scheduler_job_log; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.scheduler_job_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    job_name character varying(100) NOT NULL,
    job_date date NOT NULL,
    status public.scheduler_job_status DEFAULT 'RUNNING'::public.scheduler_job_status NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    finished_at timestamp with time zone,
    rows_processed integer,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_scheduler_job_log_finished CHECK ((((status = ANY (ARRAY['COMPLETED'::public.scheduler_job_status, 'FAILED'::public.scheduler_job_status])) AND (finished_at IS NOT NULL)) OR ((status = 'RUNNING'::public.scheduler_job_status) AND (finished_at IS NULL))))
);


ALTER TABLE public.scheduler_job_log OWNER TO juice_user;

--
-- Name: subscription_change_requests; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.subscription_change_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    subscription_id uuid NOT NULL,
    change_type public.change_request_type NOT NULL,
    new_value text NOT NULL,
    effective_date date NOT NULL,
    status public.change_request_status DEFAULT 'APPROVED'::public.change_request_status NOT NULL,
    requested_by_type public.change_request_actor_type NOT NULL,
    requested_by_user_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.subscription_change_requests OWNER TO juice_user;

--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.subscriptions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity integer NOT NULL,
    start_date date NOT NULL,
    status public.subscription_status DEFAULT 'PENDING_START'::public.subscription_status NOT NULL,
    pause_reason public.pause_reason,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_subscriptions_pause_reason CHECK ((((status = 'PAUSED'::public.subscription_status) AND (pause_reason IS NOT NULL)) OR ((status <> 'PAUSED'::public.subscription_status) AND (pause_reason IS NULL)))),
    CONSTRAINT chk_subscriptions_quantity_positive CHECK ((quantity >= 1))
);


ALTER TABLE public.subscriptions OWNER TO juice_user;

--
-- Name: users; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(150),
    phone character varying(15),
    role public.user_role NOT NULL,
    auth_provider public.auth_provider NOT NULL,
    google_id character varying(255),
    phone_verified boolean DEFAULT false NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    onboarding_completed boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_users_google_auth CHECK ((((auth_provider = 'GOOGLE'::public.auth_provider) AND (google_id IS NOT NULL)) OR ((auth_provider = 'ADMIN_PASSWORD'::public.auth_provider) AND (google_id IS NULL))))
);


ALTER TABLE public.users OWNER TO juice_user;

--
-- Name: wallet_ledger; Type: TABLE; Schema: public; Owner: juice_user
--

CREATE TABLE public.wallet_ledger (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid NOT NULL,
    order_id uuid,
    entry_type public.wallet_entry_type NOT NULL,
    source_type public.wallet_source_type NOT NULL,
    amount_paise bigint NOT NULL,
    running_balance_paise bigint NOT NULL,
    description text,
    reference character varying(100),
    created_by_user_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_wallet_ledger_amount_positive CHECK ((amount_paise > 0)),
    CONSTRAINT chk_wallet_ledger_system_adjustment_no_actor CHECK ((((source_type = 'SYSTEM_ADJUSTMENT'::public.wallet_source_type) AND (created_by_user_id IS NULL)) OR (source_type <> 'SYSTEM_ADJUSTMENT'::public.wallet_source_type)))
);


ALTER TABLE public.wallet_ledger OWNER TO juice_user;

--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: admin_audit_log pk_admin_audit_log; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT pk_admin_audit_log PRIMARY KEY (id);


--
-- Name: admin_credentials pk_admin_credentials; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.admin_credentials
    ADD CONSTRAINT pk_admin_credentials PRIMARY KEY (id);


--
-- Name: business_holidays pk_business_holidays; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.business_holidays
    ADD CONSTRAINT pk_business_holidays PRIMARY KEY (id);


--
-- Name: delivery_addresses pk_delivery_addresses; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_addresses
    ADD CONSTRAINT pk_delivery_addresses PRIMARY KEY (id);


--
-- Name: delivery_records pk_delivery_records; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_records
    ADD CONSTRAINT pk_delivery_records PRIMARY KEY (id);


--
-- Name: delivery_sheet_snapshots pk_delivery_sheet_snapshots; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_sheet_snapshots
    ADD CONSTRAINT pk_delivery_sheet_snapshots PRIMARY KEY (id);


--
-- Name: orders pk_orders; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT pk_orders PRIMARY KEY (id);


--
-- Name: product_price_history pk_product_price_history; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.product_price_history
    ADD CONSTRAINT pk_product_price_history PRIMARY KEY (id);


--
-- Name: products pk_products; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT pk_products PRIMARY KEY (id);


--
-- Name: recharge_request_log pk_recharge_request_log; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.recharge_request_log
    ADD CONSTRAINT pk_recharge_request_log PRIMARY KEY (customer_id);


--
-- Name: refresh_tokens pk_refresh_tokens; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT pk_refresh_tokens PRIMARY KEY (id);


--
-- Name: scheduler_job_log pk_scheduler_job_log; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.scheduler_job_log
    ADD CONSTRAINT pk_scheduler_job_log PRIMARY KEY (id);


--
-- Name: subscription_change_requests pk_subscription_change_requests; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscription_change_requests
    ADD CONSTRAINT pk_subscription_change_requests PRIMARY KEY (id);


--
-- Name: subscriptions pk_subscriptions; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT pk_subscriptions PRIMARY KEY (id);


--
-- Name: users pk_users; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT pk_users PRIMARY KEY (id);


--
-- Name: wallet_ledger pk_wallet_ledger; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.wallet_ledger
    ADD CONSTRAINT pk_wallet_ledger PRIMARY KEY (id);


--
-- Name: admin_credentials uq_admin_credentials_user_id; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.admin_credentials
    ADD CONSTRAINT uq_admin_credentials_user_id UNIQUE (user_id);


--
-- Name: business_holidays uq_business_holidays_date; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.business_holidays
    ADD CONSTRAINT uq_business_holidays_date UNIQUE (holiday_date);


--
-- Name: delivery_addresses uq_delivery_addresses_customer_id; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_addresses
    ADD CONSTRAINT uq_delivery_addresses_customer_id UNIQUE (customer_id);


--
-- Name: delivery_records uq_delivery_records_order_id; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_records
    ADD CONSTRAINT uq_delivery_records_order_id UNIQUE (order_id);


--
-- Name: delivery_sheet_snapshots uq_delivery_sheet_snapshots_date; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_sheet_snapshots
    ADD CONSTRAINT uq_delivery_sheet_snapshots_date UNIQUE (delivery_date);


--
-- Name: orders uq_orders_idempotency_key; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key);


--
-- Name: scheduler_job_log uq_scheduler_job_log_name_date; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.scheduler_job_log
    ADD CONSTRAINT uq_scheduler_job_log_name_date UNIQUE (job_name, job_date);


--
-- Name: users uq_users_google_id; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_google_id UNIQUE (google_id);


--
-- Name: users uq_users_phone; Type: CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_phone UNIQUE (phone);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_admin_audit_log_acting_admin; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_admin_audit_log_acting_admin ON public.admin_audit_log USING btree (acting_admin, created_at DESC);


--
-- Name: idx_admin_audit_log_created_at; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_admin_audit_log_created_at ON public.admin_audit_log USING btree (created_at DESC);


--
-- Name: idx_admin_audit_log_target; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_admin_audit_log_target ON public.admin_audit_log USING btree (target_entity, target_id);


--
-- Name: idx_delivery_records_delivery_date_status; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_delivery_records_delivery_date_status ON public.delivery_records USING btree (delivery_date, status);


--
-- Name: idx_orders_customer_delivery_date; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_orders_customer_delivery_date ON public.orders USING btree (customer_id, delivery_date DESC);


--
-- Name: idx_orders_delivery_date_status; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_orders_delivery_date_status ON public.orders USING btree (delivery_date, status);


--
-- Name: idx_orders_subscription_id; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_orders_subscription_id ON public.orders USING btree (subscription_id);


--
-- Name: idx_scr_subscription_effective_status; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_scr_subscription_effective_status ON public.subscription_change_requests USING btree (subscription_id, effective_date, status);


--
-- Name: idx_wallet_ledger_customer_created_at; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE INDEX idx_wallet_ledger_customer_created_at ON public.wallet_ledger USING btree (customer_id, created_at DESC);


--
-- Name: uq_subscriptions_active_per_customer_product; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE UNIQUE INDEX uq_subscriptions_active_per_customer_product ON public.subscriptions USING btree (customer_id, product_id) WHERE (status = ANY (ARRAY['ACTIVE'::public.subscription_status, 'PAUSED'::public.subscription_status, 'PENDING_START'::public.subscription_status]));


--
-- Name: uq_wallet_ledger_order_source; Type: INDEX; Schema: public; Owner: juice_user
--

CREATE UNIQUE INDEX uq_wallet_ledger_order_source ON public.wallet_ledger USING btree (order_id, source_type) WHERE (order_id IS NOT NULL);


--
-- Name: admin_audit_log fk_admin_audit_log_acting_admin; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT fk_admin_audit_log_acting_admin FOREIGN KEY (acting_admin) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: admin_credentials fk_admin_credentials_users; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.admin_credentials
    ADD CONSTRAINT fk_admin_credentials_users FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: business_holidays fk_business_holidays_users; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.business_holidays
    ADD CONSTRAINT fk_business_holidays_users FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: delivery_addresses fk_delivery_addresses_users; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_addresses
    ADD CONSTRAINT fk_delivery_addresses_users FOREIGN KEY (customer_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: delivery_records fk_delivery_records_orders; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.delivery_records
    ADD CONSTRAINT fk_delivery_records_orders FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE RESTRICT;


--
-- Name: orders fk_orders_cancellation_admin; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_cancellation_admin FOREIGN KEY (cancellation_commented_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: orders fk_orders_customers; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_customers FOREIGN KEY (customer_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: orders fk_orders_products; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_products FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE RESTRICT;


--
-- Name: orders fk_orders_subscriptions; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk_orders_subscriptions FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id) ON DELETE RESTRICT;


--
-- Name: product_price_history fk_product_price_history_products; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.product_price_history
    ADD CONSTRAINT fk_product_price_history_products FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE RESTRICT;


--
-- Name: product_price_history fk_product_price_history_users; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.product_price_history
    ADD CONSTRAINT fk_product_price_history_users FOREIGN KEY (changed_by) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: recharge_request_log fk_recharge_request_log_users; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.recharge_request_log
    ADD CONSTRAINT fk_recharge_request_log_users FOREIGN KEY (customer_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: refresh_tokens fk_refresh_tokens_users; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: subscription_change_requests fk_scr_requested_by; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscription_change_requests
    ADD CONSTRAINT fk_scr_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: subscription_change_requests fk_scr_subscriptions; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscription_change_requests
    ADD CONSTRAINT fk_scr_subscriptions FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id) ON DELETE RESTRICT;


--
-- Name: subscriptions fk_subscriptions_created_by; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT fk_subscriptions_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: subscriptions fk_subscriptions_customers; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT fk_subscriptions_customers FOREIGN KEY (customer_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: subscriptions fk_subscriptions_products; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT fk_subscriptions_products FOREIGN KEY (product_id) REFERENCES public.products(id) ON DELETE RESTRICT;


--
-- Name: wallet_ledger fk_wallet_ledger_created_by; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.wallet_ledger
    ADD CONSTRAINT fk_wallet_ledger_created_by FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: wallet_ledger fk_wallet_ledger_customers; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.wallet_ledger
    ADD CONSTRAINT fk_wallet_ledger_customers FOREIGN KEY (customer_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: wallet_ledger fk_wallet_ledger_orders; Type: FK CONSTRAINT; Schema: public; Owner: juice_user
--

ALTER TABLE ONLY public.wallet_ledger
    ADD CONSTRAINT fk_wallet_ledger_orders FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict NO8NHYVF3oIi6bVmiOuUZzAw2YrOIN62k7xUBpX99i6VofgKK9t6sowTKtJMk4d

