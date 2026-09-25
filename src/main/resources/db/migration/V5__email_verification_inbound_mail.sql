-- Email verification: new accounts must confirm their address with a one-time code.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
-- Accounts that existed before verification was introduced are trusted.
UPDATE users SET email_verified = TRUE;

CREATE TABLE email_verification_codes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_verification_user_created ON email_verification_codes (user_id, created_at DESC);

-- Every message picked up from the inbound mailbox, and what the app did with it.
CREATE TABLE inbound_emails (
    id           BIGSERIAL PRIMARY KEY,
    message_id   VARCHAR(512) NOT NULL UNIQUE,
    from_address VARCHAR(320) NOT NULL,
    from_name    VARCHAR(200),
    subject      VARCHAR(998),
    body_text    TEXT,
    received_at  TIMESTAMPTZ  NOT NULL,
    user_id      BIGINT REFERENCES users (id) ON DELETE SET NULL,
    status       VARCHAR(20)  NOT NULL,
    handler      VARCHAR(60),
    result       TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_inbound_emails_created ON inbound_emails (created_at DESC);
CREATE INDEX idx_inbound_emails_user ON inbound_emails (user_id);
