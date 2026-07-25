-- SQLite schema (zero-setup local development).

CREATE TABLE IF NOT EXISTS users (
    id            TEXT PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS listings (
    id            TEXT PRIMARY KEY,
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         TEXT    NOT NULL,
    description   TEXT    NOT NULL DEFAULT '',
    price         INTEGER NOT NULL DEFAULT 0,
    currency      TEXT    NOT NULL DEFAULT 'USD',
    property_type TEXT    NOT NULL DEFAULT 'house',
    listing_type  TEXT    NOT NULL DEFAULT 'sale',
    bedrooms      INTEGER NOT NULL DEFAULT 0,
    bathrooms     REAL    NOT NULL DEFAULT 0,
    area_sqft     INTEGER NOT NULL DEFAULT 0,
    address       TEXT    NOT NULL DEFAULT '',
    city          TEXT    NOT NULL DEFAULT '',
    state         TEXT    NOT NULL DEFAULT '',
    zip_code      TEXT    NOT NULL DEFAULT '',
    latitude      REAL,
    longitude     REAL,
    status        TEXT    NOT NULL DEFAULT 'active',
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listings_user_id    ON listings(user_id);
CREATE INDEX IF NOT EXISTS idx_listings_city       ON listings(city);
CREATE INDEX IF NOT EXISTS idx_listings_status     ON listings(status);
CREATE INDEX IF NOT EXISTS idx_listings_created_at ON listings(created_at);

CREATE TABLE IF NOT EXISTS listing_images (
    id         TEXT PRIMARY KEY,
    listing_id TEXT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listing_images_listing_id ON listing_images(listing_id);
