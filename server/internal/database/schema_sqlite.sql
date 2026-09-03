-- SQLite schema (zero-setup local development).

CREATE TABLE IF NOT EXISTS users (
    id                  TEXT PRIMARY KEY,
    email               TEXT NOT NULL UNIQUE,
    name                TEXT NOT NULL,
    role                TEXT NOT NULL DEFAULT 'user',
    password_hash       TEXT NOT NULL,
    google_id           TEXT NOT NULL DEFAULT '',
    phone               TEXT NOT NULL DEFAULT '',
    bio                 TEXT NOT NULL DEFAULT '',
    license_no          TEXT NOT NULL DEFAULT '',
    email_verified      INTEGER NOT NULL DEFAULT 0,
    phone_verified      INTEGER NOT NULL DEFAULT 0,
    verification_status TEXT NOT NULL DEFAULT 'unverified',
    verification_score  INTEGER NOT NULL DEFAULT 0,
    verification_notes  TEXT NOT NULL DEFAULT '',
    verified_at         TEXT NOT NULL DEFAULT '',
    rating_avg          REAL NOT NULL DEFAULT 0,
    rating_count        INTEGER NOT NULL DEFAULT 0,
    created_at          TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS listings (
    id                   TEXT PRIMARY KEY,
    user_id              TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title                TEXT    NOT NULL,
    description          TEXT    NOT NULL DEFAULT '',
    price                INTEGER NOT NULL DEFAULT 0,
    currency             TEXT    NOT NULL DEFAULT 'USD',
    property_type        TEXT    NOT NULL DEFAULT 'house',
    listing_type         TEXT    NOT NULL DEFAULT 'sale',
    bedrooms             INTEGER NOT NULL DEFAULT 0,
    bathrooms            REAL    NOT NULL DEFAULT 0,
    area_sqft            INTEGER NOT NULL DEFAULT 0,
    address              TEXT    NOT NULL DEFAULT '',
    city                 TEXT    NOT NULL DEFAULT '',
    state                TEXT    NOT NULL DEFAULT '',
    zip_code             TEXT    NOT NULL DEFAULT '',
    latitude             REAL,
    longitude            REAL,
    status               TEXT    NOT NULL DEFAULT 'active',
    verification_status  TEXT    NOT NULL DEFAULT 'pending',
    verification_score   INTEGER NOT NULL DEFAULT 0,
    verification_summary TEXT    NOT NULL DEFAULT '',
    verification_flags   TEXT    NOT NULL DEFAULT '',
    verification_model   TEXT    NOT NULL DEFAULT '',
    verified_at          TEXT    NOT NULL DEFAULT '',
    last_confirmed_at    TEXT    NOT NULL DEFAULT '',
    featured_until       TEXT    NOT NULL DEFAULT '',
    report_count         INTEGER NOT NULL DEFAULT 0,
    view_count           INTEGER NOT NULL DEFAULT 0,
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listings_user_id      ON listings(user_id);
CREATE INDEX IF NOT EXISTS idx_listings_city         ON listings(city);
CREATE INDEX IF NOT EXISTS idx_listings_status       ON listings(status);
CREATE INDEX IF NOT EXISTS idx_listings_created_at   ON listings(created_at);
CREATE INDEX IF NOT EXISTS idx_listings_verification ON listings(verification_status);
CREATE INDEX IF NOT EXISTS idx_listings_geo          ON listings(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_listings_price        ON listings(price);
CREATE INDEX IF NOT EXISTS idx_listings_featured     ON listings(featured_until);

CREATE TABLE IF NOT EXISTS listing_images (
    id         TEXT PRIMARY KEY,
    listing_id TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listing_images_listing_id ON listing_images(listing_id);

-- Scam / misleading-listing reports. The survey's top pain point: 57% of
-- respondents had hit a scam or misleading listing.
CREATE TABLE IF NOT EXISTS listing_reports (
    id          TEXT PRIMARY KEY,
    listing_id  TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    reporter_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason      TEXT NOT NULL,
    details     TEXT NOT NULL DEFAULT '',
    status      TEXT NOT NULL DEFAULT 'open',
    resolution  TEXT NOT NULL DEFAULT '',
    created_at  TEXT NOT NULL,
    resolved_at TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_reports_listing ON listing_reports(listing_id);
CREATE INDEX IF NOT EXISTS idx_reports_status  ON listing_reports(status);

-- In-app chat: 59% of respondents ranked it their second-most useful feature.
CREATE TABLE IF NOT EXISTS conversations (
    id              TEXT PRIMARY KEY,
    listing_id      TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    inquirer_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    owner_id        TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_message_at TEXT NOT NULL DEFAULT '',
    created_at      TEXT NOT NULL,
    UNIQUE (listing_id, inquirer_id)
);

CREATE INDEX IF NOT EXISTS idx_conversations_inquirer ON conversations(inquirer_id);
CREATE INDEX IF NOT EXISTS idx_conversations_owner    ON conversations(owner_id);

CREATE TABLE IF NOT EXISTS messages (
    id              TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body            TEXT NOT NULL,
    read_at         TEXT NOT NULL DEFAULT '',
    created_at      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id, created_at);

-- Viewing appointments: 57% of respondents wanted in-app scheduling, and
-- "scheduling viewings" was the #4 cause of transaction delays.
CREATE TABLE IF NOT EXISTS viewing_requests (
    id            TEXT PRIMARY KEY,
    listing_id    TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    requester_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    owner_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scheduled_for TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'requested',
    note          TEXT NOT NULL DEFAULT '',
    response_note TEXT NOT NULL DEFAULT '',
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_viewings_requester ON viewing_requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_viewings_owner     ON viewing_requests(owner_id);
CREATE INDEX IF NOT EXISTS idx_viewings_listing   ON viewing_requests(listing_id);

-- Agent / owner ratings: 39% of respondents asked for ratings & reviews.
CREATE TABLE IF NOT EXISTS reviews (
    id              TEXT PRIMARY KEY,
    subject_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    listing_id      TEXT NOT NULL DEFAULT '',
    rating          INTEGER NOT NULL,
    comment         TEXT NOT NULL DEFAULT '',
    created_at      TEXT NOT NULL,
    UNIQUE (subject_user_id, author_id)
);

CREATE INDEX IF NOT EXISTS idx_reviews_subject ON reviews(subject_user_id);

-- Saved searches with alerting, for the "instant alerts / saved searches"
-- requests in the free-text answers.
CREATE TABLE IF NOT EXISTS saved_searches (
    id              TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    query_json      TEXT NOT NULL,
    alerts_enabled  INTEGER NOT NULL DEFAULT 1,
    last_alerted_at TEXT NOT NULL DEFAULT '',
    created_at      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_saved_searches_user ON saved_searches(user_id);
