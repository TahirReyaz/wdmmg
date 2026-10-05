-- Optional user-defined tags ("Goa trip", "Wedding") that group expenses across categories,
-- so a user can see what a trip or an event cost in total. One tag per expense, at most.
CREATE TABLE tags (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(60)  NOT NULL,
    color      VARCHAR(9)   NOT NULL DEFAULT '#2a78d6',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Names are unique per user, ignoring case ("Goa trip" = "goa trip").
CREATE UNIQUE INDEX uq_tags_user_name ON tags (user_id, lower(name));

-- Deleting a tag keeps its expenses; they just become untagged.
ALTER TABLE expenses ADD COLUMN tag_id BIGINT REFERENCES tags(id) ON DELETE SET NULL;
CREATE INDEX idx_expenses_tag ON expenses (tag_id) WHERE tag_id IS NOT NULL;
