-- The whole schema of a fresh app. Every table that holds a user's data
-- references users(id) ON DELETE CASCADE: DELETE /me and the web deletion page
-- remove the user row and rely on the cascade for everything else, and the
-- privacy page promises exactly that.

CREATE TABLE users (
    id            UUID        PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    name          TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Only the SHA-256 of a refresh token is stored. A row's id is the session id
-- carried in the access token's "sid" claim.
CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL
);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens(user_id);

CREATE TABLE items (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX items_user_id_idx ON items(user_id);
