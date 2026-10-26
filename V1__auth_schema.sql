CREATE TABLE app_user (
    id                 UUID                     PRIMARY KEY,
    username           VARCHAR(100)             NOT NULL UNIQUE,
    password_hash      VARCHAR(255)             NOT NULL,
    role               VARCHAR(20)              NOT NULL,
    enabled            BOOLEAN                  NOT NULL,
    failed_attempts    INTEGER                  NOT NULL,
    locked_until       TIMESTAMP WITH TIME ZONE,
    token_version      INTEGER                  NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_token (
    id          UUID                     PRIMARY KEY,
    token_hash  VARCHAR(64)              NOT NULL UNIQUE,
    user_id     UUID                     NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id   UUID                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked     BOOLEAN                  NOT NULL
);

CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);

CREATE TABLE revoked_access_token (
    jti        VARCHAR(64)              PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_revoked_access_token_expires ON revoked_access_token (expires_at);
