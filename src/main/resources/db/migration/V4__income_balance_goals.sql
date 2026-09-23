-- ---------------------------------------------------------------- bank balance & salary settings
ALTER TABLE users
    ADD COLUMN opening_balance         NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN opening_balance_date    DATE,
    ADD COLUMN salary_reminder         BOOLEAN       NOT NULL DEFAULT TRUE,
    ADD COLUMN salary_day              INT           NOT NULL DEFAULT 1 CHECK (salary_day BETWEEN 1 AND 28),
    -- "YYYY-MM" of the month the user said "no salary this month" for
    ADD COLUMN salary_prompt_dismissed VARCHAR(7);

-- ---------------------------------------------------------------- money in
CREATE TABLE incomes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(150)  NOT NULL,
    amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    income_date DATE          NOT NULL,
    type        VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    notes       VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_incomes_user_date ON incomes(user_id, income_date DESC);

-- ---------------------------------------------------------------- savings goals
-- Money in a goal stays in the bank; it's just set aside, so it reduces "available", not "balance".
CREATE TABLE goals (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(100)  NOT NULL,
    target_amount NUMERIC(12,2) NOT NULL CHECK (target_amount > 0),
    target_date   DATE,
    saved_amount  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (saved_amount >= 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_goals_user ON goals(user_id);

CREATE TABLE goal_contributions (
    id         BIGSERIAL PRIMARY KEY,
    goal_id    BIGINT        NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    amount     NUMERIC(12,2) NOT NULL CHECK (amount <> 0), -- negative = taken out
    note       VARCHAR(255),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_goal_contributions_goal ON goal_contributions(goal_id, created_at DESC);
