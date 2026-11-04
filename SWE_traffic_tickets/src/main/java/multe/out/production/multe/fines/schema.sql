-- PostgreSQL schema for the speed camera fines system.
-- Usage:  createdb fines && psql -d fines -f db/schema.sql -f db/data.sql

DROP TABLE IF EXISTS fine_events, fines, plate_ownerships, speed_cameras, users CASCADE;
DROP SEQUENCE IF EXISTS fine_id_seq;

-- Users: tax_code is the login username. Passwords are stored as PBKDF2 (salt + hash), never in clear text.
CREATE TABLE users (
    id            INTEGER PRIMARY KEY,
    tax_code      TEXT    NOT NULL UNIQUE,
    full_name     TEXT    NOT NULL,
    password_salt BYTEA   NOT NULL,
    password_hash BYTEA   NOT NULL,
    role          TEXT    NOT NULL CHECK (role IN ('CITIZEN', 'POLICE_OFFICER', 'ADMINISTRATOR', 'JUDGE')),
    badge_number  TEXT,
    CHECK (badge_number IS NULL OR role = 'POLICE_OFFICER')
);

CREATE TABLE speed_cameras (
    id          TEXT PRIMARY KEY,
    address     TEXT NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    speed_limit INTEGER NOT NULL CHECK (speed_limit > 0)
);

-- Ownership history of each plate: "from instant SINCE the owner is OWNER_ID".
-- It answers "who owned the plate at the time of the detection?" even after a sale.
CREATE TABLE plate_ownerships (
    plate    TEXT        NOT NULL CHECK (plate = upper(btrim(plate))),
    owner_id INTEGER     NOT NULL REFERENCES users(id),
    since    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (plate, since)
);

CREATE SEQUENCE fine_id_seq;

-- A fine keeps a snapshot of the detection (speed, limit in force, instant): changing the speed camera
-- limit later does not alter fines already issued. The owner is the one at the time of the detection.
CREATE TABLE fines (
    id             INTEGER     PRIMARY KEY DEFAULT nextval('fine_id_seq'),
    owner_id       INTEGER     NOT NULL REFERENCES users(id),
    amount_cents   BIGINT      NOT NULL CHECK (amount_cents > 0),
    due_date       DATE        NOT NULL,
    state          TEXT        NOT NULL CHECK (state IN ('ISSUED', 'PAID', 'EXPIRED', 'CANCELLED')),
    camera_id      TEXT        NOT NULL REFERENCES speed_cameras(id),
    plate          TEXT        NOT NULL,
    detected_speed INTEGER     NOT NULL CHECK (detected_speed > 0),
    speed_limit    INTEGER     NOT NULL CHECK (speed_limit > 0),
    detected_at    TIMESTAMPTZ NOT NULL,
    -- cancellation ruling: present only if the fine is CANCELLED
    judge_id       INTEGER     REFERENCES users(id),
    ruling_reason  TEXT,
    ruling_date    DATE,
    CHECK (detected_speed > speed_limit),
    CHECK ((state = 'CANCELLED') = (judge_id IS NOT NULL)),
    CHECK (judge_id IS NULL OR (ruling_reason IS NOT NULL AND ruling_date IS NOT NULL))
);
CREATE INDEX idx_fines_owner ON fines(owner_id);

-- Event history of every fine (written by the JdbcFineEventLog observer).
CREATE TABLE fine_events (
    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fine_id     INTEGER     NOT NULL REFERENCES fines(id),
    event       TEXT        NOT NULL CHECK (event IN ('ISSUED', 'PAID', 'EXPIRED', 'CANCELLED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    author_id   INTEGER     REFERENCES users(id)    -- only for CANCELLED: the judge
);
CREATE INDEX idx_fine_events_fine ON fine_events(fine_id);
