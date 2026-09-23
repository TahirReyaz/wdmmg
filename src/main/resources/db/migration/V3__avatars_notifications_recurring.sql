-- ---------------------------------------------------------------- profile pictures
-- The key is random so avatar URLs can be public (for <img> tags) without being guessable.
CREATE TABLE user_avatars (
    avatar_key   UUID PRIMARY KEY,
    user_id      BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(40) NOT NULL,
    data         BYTEA       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE users ADD COLUMN avatar_key UUID;

-- ---------------------------------------------------------------- notifications
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(40)   NOT NULL,
    title      VARCHAR(255)  NOT NULL,
    link       VARCHAR(255),
    amount     NUMERIC(12,2),
    ref_id     BIGINT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;

-- ---------------------------------------------------------------- recurring expenses
CREATE TABLE recurring_expenses (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                  VARCHAR(150)  NOT NULL,
    amount                NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    category_id           BIGINT        NOT NULL REFERENCES categories(id),
    payment_method        VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    notes                 VARCHAR(1000),
    frequency             VARCHAR(10)   NOT NULL,
    interval_count        INT           NOT NULL DEFAULT 1 CHECK (interval_count BETWEEN 1 AND 365),
    start_date            DATE          NOT NULL,
    end_date              DATE,
    occurrences_generated INT           NOT NULL DEFAULT 0,
    next_due_date         DATE,
    active                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_recurring_user ON recurring_expenses(user_id);
CREATE INDEX idx_recurring_due ON recurring_expenses(next_due_date) WHERE active;

CREATE TABLE recurring_occurrences (
    id           BIGSERIAL PRIMARY KEY,
    recurring_id BIGINT        NOT NULL REFERENCES recurring_expenses(id) ON DELETE CASCADE,
    user_id      BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    due_date     DATE          NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    status       VARCHAR(12)   NOT NULL DEFAULT 'PENDING',
    expense_id   BIGINT        REFERENCES expenses(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    UNIQUE (recurring_id, due_date)
);
CREATE INDEX idx_occurrences_user_status ON recurring_occurrences(user_id, status);
