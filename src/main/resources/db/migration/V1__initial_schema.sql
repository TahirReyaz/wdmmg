-- ---------------------------------------------------------------------------
-- Where did my money go - initial schema
--
-- Conventions:
--   * UUIDv7 primary keys, generated in the application (time-ordered).
--   * All money is BIGINT minor units (paise for INR) + an ISO-4217 code.
--   * All timestamps are TIMESTAMPTZ.
--   * Enum-like columns are TEXT + CHECK rather than PostgreSQL ENUM types,
--     so adding a value is a migration instead of a type rewrite, and
--     Hibernate needs no custom type casting.
--   * Anything a user can remove is soft deleted via deleted_at.
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    email             CITEXT      NOT NULL UNIQUE,
    display_name      TEXT        NOT NULL CHECK (length(display_name) BETWEEN 1 AND 80),
    avatar_url        TEXT,
    base_currency     CHAR(3)     NOT NULL DEFAULT 'INR',
    timezone          TEXT        NOT NULL DEFAULT 'Asia/Kolkata',
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);

CREATE TABLE user_credentials (
    user_id         UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    password_hash   TEXT        NOT NULL,
    password_set_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE oauth_accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         TEXT        NOT NULL CHECK (provider IN ('GOOGLE', 'GITHUB', 'APPLE')),
    provider_user_id TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  TEXT        NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    user_agent  TEXT,
    CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_family ON refresh_tokens (family_id);

-- ---------------------------------------------------------------------------
-- Taxonomy
-- ---------------------------------------------------------------------------

CREATE TABLE expense_categories (
    id          UUID PRIMARY KEY,
    user_id     UUID REFERENCES users (id) ON DELETE CASCADE, -- NULL = system category
    name        TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    icon        TEXT,
    color_hex   CHAR(7) CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    parent_id   UUID REFERENCES expense_categories (id) ON DELETE SET NULL,
    sort_order  SMALLINT    NOT NULL DEFAULT 0,
    is_archived BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_category_per_user
    ON expense_categories (COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));

CREATE TABLE trips (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          TEXT        NOT NULL,
    description   TEXT,
    start_date    DATE,
    end_date      DATE,
    budget_minor  BIGINT CHECK (budget_minor >= 0),
    currency_code CHAR(3)     NOT NULL DEFAULT 'INR',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_trips_user ON trips (user_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Groups (schema only in this phase; endpoints land in a later phase)
-- ---------------------------------------------------------------------------

CREATE TABLE groups (
    id               UUID PRIMARY KEY,
    name             TEXT        NOT NULL,
    description      TEXT,
    default_currency CHAR(3)     NOT NULL DEFAULT 'INR',
    trip_id          UUID REFERENCES trips (id) ON DELETE SET NULL,
    created_by       UUID        NOT NULL REFERENCES users (id),
    simplify_debts   BOOLEAN     NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

CREATE TABLE group_members (
    id        UUID PRIMARY KEY,
    group_id  UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role      TEXT        NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at   TIMESTAMPTZ,
    UNIQUE (group_id, user_id)
);

CREATE INDEX idx_member_user ON group_members (user_id) WHERE left_at IS NULL;

CREATE TABLE group_expenses (
    id            UUID PRIMARY KEY,
    group_id      UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    description   TEXT        NOT NULL,
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency_code CHAR(3)     NOT NULL,
    paid_by       UUID        NOT NULL REFERENCES users (id),
    category_id   UUID REFERENCES expense_categories (id) ON DELETE SET NULL,
    trip_id       UUID REFERENCES trips (id) ON DELETE SET NULL,
    split_method  TEXT        NOT NULL DEFAULT 'EQUAL'
        CHECK (split_method IN ('EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES')),
    spent_at      TIMESTAMPTZ NOT NULL,
    created_by    UUID        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);

CREATE INDEX idx_group_expense_time ON group_expenses (group_id, spent_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE group_expense_splits (
    id               UUID PRIMARY KEY,
    group_expense_id UUID   NOT NULL REFERENCES group_expenses (id) ON DELETE CASCADE,
    user_id          UUID   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    share_minor      BIGINT NOT NULL CHECK (share_minor >= 0),
    share_ratio      NUMERIC(9, 6),
    UNIQUE (group_expense_id, user_id)
);

CREATE INDEX idx_split_user ON group_expense_splits (user_id);

-- Shares must sum to the parent total. The service layer checks this first and
-- returns a friendly 422; this trigger is the backstop so no other writer -
-- a migration, a script, a future module - can leave a group expense
-- unbalanced. Deferred, so a multi-row insert is judged once at COMMIT.
CREATE OR REPLACE FUNCTION assert_splits_balance() RETURNS TRIGGER AS
$$
DECLARE
    parent_id UUID;
    total     BIGINT;
    summed    BIGINT;
BEGIN
    parent_id := COALESCE(NEW.group_expense_id, OLD.group_expense_id);

    SELECT amount_minor INTO total FROM group_expenses WHERE id = parent_id;

    -- The parent is gone (a cascading delete), so there is nothing to balance.
    IF total IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(SUM(share_minor), 0) INTO summed
      FROM group_expense_splits
     WHERE group_expense_id = parent_id;

    IF summed <> total THEN
        RAISE EXCEPTION 'splits sum to % but group expense % totals %', summed, parent_id, total
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_splits_balance
    AFTER INSERT OR UPDATE OR DELETE
    ON group_expense_splits
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION assert_splits_balance();

-- ---------------------------------------------------------------------------
-- Recurring payments (schema only in this phase)
-- ---------------------------------------------------------------------------

CREATE TABLE recurring_payments (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label           TEXT        NOT NULL,
    kind            TEXT        NOT NULL CHECK (kind IN ('SIP', 'SUBSCRIPTION', 'EMI', 'OTHER')),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency_code   CHAR(3)     NOT NULL,
    category_id     UUID REFERENCES expense_categories (id) ON DELETE SET NULL,
    payment_method  TEXT,
    frequency       TEXT        NOT NULL
        CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    interval_count  SMALLINT    NOT NULL DEFAULT 1 CHECK (interval_count > 0),
    day_of_month    SMALLINT CHECK (day_of_month BETWEEN 1 AND 31),
    day_of_week     SMALLINT CHECK (day_of_week BETWEEN 1 AND 7),
    start_date      DATE        NOT NULL,
    end_date        DATE,
    max_occurrences INT CHECK (max_occurrences > 0),
    timezone        TEXT        NOT NULL DEFAULT 'Asia/Kolkata',
    is_active       BOOLEAN     NOT NULL DEFAULT true,
    auto_post       BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_recurring_active ON recurring_payments (user_id) WHERE is_active AND deleted_at IS NULL;

CREATE TABLE payment_occurrences (
    id                   UUID PRIMARY KEY,
    recurring_payment_id UUID        NOT NULL REFERENCES recurring_payments (id) ON DELETE CASCADE,
    due_at               TIMESTAMPTZ NOT NULL,
    amount_minor         BIGINT      NOT NULL CHECK (amount_minor > 0),
    state                TEXT        NOT NULL DEFAULT 'SCHEDULED'
        CHECK (state IN ('SCHEDULED', 'POSTED', 'SKIPPED', 'FAILED', 'CANCELLED')),
    expense_id           UUID,
    attempt_count        SMALLINT    NOT NULL DEFAULT 0,
    last_error           TEXT,
    posted_at            TIMESTAMPTZ,
    UNIQUE (recurring_payment_id, due_at),
    CHECK (state <> 'POSTED' OR expense_id IS NOT NULL)
);

CREATE INDEX idx_occurrence_due ON payment_occurrences (due_at) WHERE state = 'SCHEDULED';

-- ---------------------------------------------------------------------------
-- The personal ledger
-- ---------------------------------------------------------------------------

CREATE TABLE expenses (
    id                   UUID PRIMARY KEY,
    user_id              UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount_minor         BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency_code        CHAR(3)     NOT NULL,
    spent_at             TIMESTAMPTZ NOT NULL,
    category_id          UUID REFERENCES expense_categories (id) ON DELETE SET NULL,
    trip_id              UUID REFERENCES trips (id) ON DELETE SET NULL,
    merchant             TEXT CHECK (merchant IS NULL OR length(merchant) <= 120),
    note                 TEXT CHECK (note IS NULL OR length(note) <= 2000),
    payment_method       TEXT CHECK (payment_method IN
                                     ('CASH', 'UPI', 'CARD', 'NETBANKING', 'WALLET', 'OTHER')),
    origin               TEXT        NOT NULL DEFAULT 'MANUAL'
        CHECK (origin IN ('MANUAL', 'AUTOPAY', 'GROUP')),
    source_occurrence_id UUID REFERENCES payment_occurrences (id) ON DELETE SET NULL,
    source_split_id      UUID REFERENCES group_expense_splits (id) ON DELETE CASCADE,
    reimbursement        TEXT        NOT NULL DEFAULT 'NOT_APPLICABLE'
        CHECK (reimbursement IN ('NOT_APPLICABLE', 'PENDING', 'SETTLED')),
    idempotency_key      TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,

    CONSTRAINT chk_origin_source CHECK (
        (origin = 'MANUAL' AND source_occurrence_id IS NULL AND source_split_id IS NULL) OR
        (origin = 'AUTOPAY' AND source_occurrence_id IS NOT NULL AND source_split_id IS NULL) OR
        (origin = 'GROUP' AND source_split_id IS NOT NULL AND source_occurrence_id IS NULL)
        )
);

ALTER TABLE payment_occurrences
    ADD CONSTRAINT fk_occurrence_expense FOREIGN KEY (expense_id) REFERENCES expenses (id) ON DELETE SET NULL;

-- The workhorse: "my expenses, newest first, within a date window".
CREATE INDEX idx_expenses_user_time ON expenses (user_id, spent_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_user_cat_time ON expenses (user_id, category_id, spent_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_expenses_trip ON expenses (trip_id) WHERE trip_id IS NOT NULL;
CREATE INDEX idx_expenses_merchant_trgm ON expenses USING gin (merchant gin_trgm_ops);

CREATE UNIQUE INDEX uq_expense_idem ON expenses (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_expense_occurrence ON expenses (source_occurrence_id) WHERE source_occurrence_id IS NOT NULL;
CREATE UNIQUE INDEX uq_expense_split ON expenses (source_split_id) WHERE source_split_id IS NOT NULL;

CREATE TABLE expense_attachments (
    id         UUID PRIMARY KEY,
    expense_id UUID        NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    object_key TEXT        NOT NULL,
    mime_type  TEXT        NOT NULL,
    size_bytes INT         NOT NULL CHECK (size_bytes BETWEEN 1 AND 10485760),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_expense ON expense_attachments (expense_id);

-- ---------------------------------------------------------------------------
-- Settlement (schema only in this phase)
-- ---------------------------------------------------------------------------

CREATE TABLE settlements (
    id            UUID PRIMARY KEY,
    group_id      UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    payer_id      UUID        NOT NULL REFERENCES users (id),
    payee_id      UUID        NOT NULL REFERENCES users (id),
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency_code CHAR(3)     NOT NULL,
    method        TEXT CHECK (method IN ('CASH', 'UPI', 'BANK', 'OTHER')),
    note          TEXT,
    settled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by   UUID        NOT NULL REFERENCES users (id),
    confirmed_at  TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    CHECK (payer_id <> payee_id)
);

CREATE INDEX idx_settlement_group ON settlements (group_id, settled_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE group_balances (
    group_id      UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    currency_code CHAR(3)     NOT NULL,
    net_minor     BIGINT      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id, currency_code)
);

-- ---------------------------------------------------------------------------
-- Analytics rollup
--
-- category_id and group_id are nullable in meaning but NOT NULL in storage:
-- the all-zero UUID stands for "uncategorised" / "not a group expense" so the
-- primary key stays usable (NULLs are never equal in a unique index).
-- ---------------------------------------------------------------------------

CREATE TABLE expense_daily_rollup (
    user_id       UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day           DATE    NOT NULL,
    category_id   UUID    NOT NULL,
    group_id      UUID    NOT NULL,
    origin        TEXT    NOT NULL CHECK (origin IN ('MANUAL', 'AUTOPAY', 'GROUP')),
    currency_code CHAR(3) NOT NULL,
    total_minor   BIGINT  NOT NULL DEFAULT 0,
    txn_count     INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day, category_id, group_id, origin, currency_code)
);

CREATE INDEX idx_rollup_user_day ON expense_daily_rollup (user_id, day DESC);

-- ---------------------------------------------------------------------------
-- Infrastructure
-- ---------------------------------------------------------------------------

CREATE TABLE scheduler_locks (
    name         TEXT PRIMARY KEY,
    locked_at    TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ NOT NULL,
    locked_by    TEXT        NOT NULL
);

CREATE TABLE outbox (
    id         UUID PRIMARY KEY,
    topic      TEXT        NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ,
    attempts   SMALLINT    NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE sent_at IS NULL;
