CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(255)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    role          VARCHAR(20)   NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL UNIQUE,
    icon       VARCHAR(40),
    color      VARCHAR(9)   NOT NULL DEFAULT '#64748b',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE expenses (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(150)  NOT NULL,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    expense_date   DATE          NOT NULL,
    category_id    BIGINT        NOT NULL REFERENCES categories(id),
    payment_method VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    notes          VARCHAR(1000),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_expenses_user_date ON expenses(user_id, expense_date DESC);
CREATE INDEX idx_expenses_category ON expenses(category_id);

CREATE TABLE expense_groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_by  BIGINT       NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT      NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_members_user ON group_members(user_id);

CREATE TABLE group_expenses (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT        NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
    paid_by      BIGINT        NOT NULL REFERENCES users(id),
    created_by   BIGINT        NOT NULL REFERENCES users(id),
    name         VARCHAR(150)  NOT NULL,
    amount       NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    expense_date DATE          NOT NULL,
    category_id  BIGINT        NOT NULL REFERENCES categories(id),
    split_type   VARCHAR(20)   NOT NULL,
    notes        VARCHAR(1000),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_group_expenses_group_date ON group_expenses(group_id, expense_date DESC);

CREATE TABLE group_expense_shares (
    id               BIGSERIAL PRIMARY KEY,
    group_expense_id BIGINT        NOT NULL REFERENCES group_expenses(id) ON DELETE CASCADE,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    amount           NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    UNIQUE (group_expense_id, user_id)
);
CREATE INDEX idx_shares_user ON group_expense_shares(user_id);

CREATE TABLE settlements (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT        NOT NULL REFERENCES expense_groups(id) ON DELETE CASCADE,
    from_user  BIGINT        NOT NULL REFERENCES users(id),
    to_user    BIGINT        NOT NULL REFERENCES users(id),
    amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    settled_on DATE          NOT NULL,
    note       VARCHAR(255),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (from_user <> to_user)
);
CREATE INDEX idx_settlements_group ON settlements(group_id);
