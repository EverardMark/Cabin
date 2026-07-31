-- MySQL 8 schema (InnoDB, utf8mb4). Timestamps are stored as RFC3339 strings
-- to match the SQLite storage format, so the application code is identical.

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'user',
    password_hash VARCHAR(255) NOT NULL,
    google_id     VARCHAR(255) NOT NULL DEFAULT '',
    created_at    VARCHAR(40)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listings (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(36)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    price         BIGINT       NOT NULL DEFAULT 0,
    currency      VARCHAR(8)   NOT NULL DEFAULT 'USD',
    property_type VARCHAR(32)  NOT NULL DEFAULT 'house',
    listing_type  VARCHAR(16)  NOT NULL DEFAULT 'sale',
    bedrooms      INT          NOT NULL DEFAULT 0,
    bathrooms     DOUBLE       NOT NULL DEFAULT 0,
    area_sqft     INT          NOT NULL DEFAULT 0,
    address       VARCHAR(255) NOT NULL DEFAULT '',
    city          VARCHAR(128) NOT NULL DEFAULT '',
    state         VARCHAR(64)  NOT NULL DEFAULT '',
    zip_code      VARCHAR(16)  NOT NULL DEFAULT '',
    latitude      DOUBLE       NULL,
    longitude     DOUBLE       NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active',
    created_at    VARCHAR(40)  NOT NULL,
    updated_at    VARCHAR(40)  NOT NULL,
    CONSTRAINT fk_listings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_listings_user_id (user_id),
    INDEX idx_listings_city (city),
    INDEX idx_listings_status (status),
    INDEX idx_listings_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listing_images (
    id         VARCHAR(36) NOT NULL PRIMARY KEY,
    listing_id VARCHAR(36) NOT NULL,
    url        TEXT        NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at VARCHAR(40) NOT NULL,
    CONSTRAINT fk_images_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    INDEX idx_listing_images_listing_id (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
