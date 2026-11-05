-- A town is one shared budget. Everything here hangs off users or towns with
-- ON DELETE CASCADE, so deleting an account (or a town) leaves no orphan rows.

CREATE TABLE towns (
    id          uuid PRIMARY KEY,
    owner_id    uuid NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    name        text NOT NULL,
    budget_cents bigint NOT NULL,
    payday      integer NOT NULL,
    currency    text NOT NULL,
    bills_first boolean NOT NULL,
    time_zone   text NOT NULL,
    created_at  timestamptz NOT NULL
);

CREATE TABLE town_members (
    town_id     uuid NOT NULL REFERENCES towns (id) ON DELETE CASCADE,
    user_id     uuid NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    initial     text NOT NULL,
    color_index integer NOT NULL,
    role        text NOT NULL,
    can_edit    boolean NOT NULL,
    can_manage  boolean NOT NULL,
    joined_at   timestamptz NOT NULL,
    PRIMARY KEY (town_id, user_id),
    UNIQUE (town_id, color_index)
);

CREATE TABLE invites (
    code        text PRIMARY KEY,
    town_id     uuid NOT NULL REFERENCES towns (id) ON DELETE CASCADE,
    can_edit    boolean NOT NULL,
    can_manage  boolean NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz
);

CREATE TABLE categories (
    id          uuid PRIMARY KEY,
    town_id     uuid NOT NULL REFERENCES towns (id) ON DELETE CASCADE,
    name        text NOT NULL,
    short       text NOT NULL,
    color_index integer NOT NULL,
    cap_cents   bigint,
    position    integer NOT NULL
);

-- One tower name per town, whatever the letter case.
CREATE UNIQUE INDEX categories_town_name ON categories (town_id, lower(name));

CREATE TABLE expenses (
    id          uuid PRIMARY KEY,
    town_id     uuid NOT NULL REFERENCES towns (id) ON DELETE CASCADE,
    category_id uuid NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    amount_cents bigint NOT NULL,
    date        date NOT NULL,
    note        text NOT NULL,
    recurring   boolean NOT NULL,
    shared      boolean NOT NULL,
    refund      boolean NOT NULL,
    paid_by     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    settled_at  timestamptz,
    created_at  timestamptz NOT NULL
);

CREATE INDEX expenses_town_date ON expenses (town_id, date);

CREATE TABLE gold_marks (
    town_id     uuid NOT NULL REFERENCES towns (id) ON DELETE CASCADE,
    date        date NOT NULL,
    marked_by   uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (town_id, date)
);

CREATE TABLE bill_skips (
    town_id     uuid NOT NULL REFERENCES towns (id) ON DELETE CASCADE,
    month_start date NOT NULL,
    series_key  text NOT NULL,
    PRIMARY KEY (town_id, month_start, series_key)
);
