-- MySQL 8 schema (InnoDB, utf8mb4). Timestamps are stored as RFC3339 strings
-- to match the SQLite storage format, so the application code is identical.

CREATE TABLE IF NOT EXISTS users (
    id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    role                VARCHAR(20)  NOT NULL DEFAULT 'user',
    password_hash       VARCHAR(255) NOT NULL,
    google_id           VARCHAR(255) NOT NULL DEFAULT '',
    apple_id            VARCHAR(255) NOT NULL DEFAULT '',
    phone               VARCHAR(32)  NOT NULL DEFAULT '',
    bio                 TEXT         NULL,
    license_no          VARCHAR(64)  NOT NULL DEFAULT '',
    email_verified      TINYINT(1)   NOT NULL DEFAULT 0,
    phone_verified      TINYINT(1)   NOT NULL DEFAULT 0,
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'unverified',
    verification_score  INT          NOT NULL DEFAULT 0,
    verification_notes  TEXT         NULL,
    verified_at         VARCHAR(40)  NOT NULL DEFAULT '',
    rating_avg          DOUBLE       NOT NULL DEFAULT 0,
    rating_count        INT          NOT NULL DEFAULT 0,
    created_at          VARCHAR(40)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS listings (
    id                   VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id              VARCHAR(36)  NOT NULL,
    title                VARCHAR(255) NOT NULL,
    description          TEXT         NOT NULL,
    price                BIGINT       NOT NULL DEFAULT 0,
    currency             VARCHAR(8)   NOT NULL DEFAULT 'USD',
    property_type        VARCHAR(32)  NOT NULL DEFAULT 'house',
    listing_type         VARCHAR(16)  NOT NULL DEFAULT 'sale',
    bedrooms             INT          NOT NULL DEFAULT 0,
    bathrooms            DOUBLE       NOT NULL DEFAULT 0,
    area_sqft            INT          NOT NULL DEFAULT 0,
    address              VARCHAR(255) NOT NULL DEFAULT '',
    city                 VARCHAR(128) NOT NULL DEFAULT '',
    state                VARCHAR(64)  NOT NULL DEFAULT '',
    zip_code             VARCHAR(16)  NOT NULL DEFAULT '',
    latitude             DOUBLE       NULL,
    longitude            DOUBLE       NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'active',
    verification_status  VARCHAR(20)  NOT NULL DEFAULT 'pending',
    verification_score   INT          NOT NULL DEFAULT 0,
    verification_summary TEXT         NULL,
    verification_flags   TEXT         NULL,
    verification_model   VARCHAR(64)  NOT NULL DEFAULT '',
    verified_at          VARCHAR(40)  NOT NULL DEFAULT '',
    last_confirmed_at    VARCHAR(40)  NOT NULL DEFAULT '',
    featured_until       VARCHAR(40)  NOT NULL DEFAULT '',
    report_count         INT          NOT NULL DEFAULT 0,
    view_count           INT          NOT NULL DEFAULT 0,
    created_at           VARCHAR(40)  NOT NULL,
    updated_at           VARCHAR(40)  NOT NULL,
    CONSTRAINT fk_listings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_listings_user_id (user_id),
    INDEX idx_listings_city (city),
    INDEX idx_listings_status (status),
    INDEX idx_listings_created_at (created_at),
    INDEX idx_listings_verification (verification_status),
    INDEX idx_listings_geo (latitude, longitude),
    INDEX idx_listings_price (price),
    INDEX idx_listings_featured (featured_until)
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

-- Scam / misleading-listing reports. The survey's top pain point: 57% of
-- respondents had hit a scam or misleading listing.
CREATE TABLE IF NOT EXISTS listing_reports (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    listing_id  VARCHAR(36)  NOT NULL,
    reporter_id VARCHAR(36)  NOT NULL,
    reason      VARCHAR(64)  NOT NULL,
    details     TEXT         NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'open',
    resolution  VARCHAR(255) NOT NULL DEFAULT '',
    created_at  VARCHAR(40)  NOT NULL,
    resolved_at VARCHAR(40)  NOT NULL DEFAULT '',
    CONSTRAINT fk_reports_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_user FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_reports_listing (listing_id),
    INDEX idx_reports_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- In-app chat: 59% of respondents ranked it their second-most useful feature.
CREATE TABLE IF NOT EXISTS conversations (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    listing_id      VARCHAR(36) NOT NULL,
    inquirer_id     VARCHAR(36) NOT NULL,
    owner_id        VARCHAR(36) NOT NULL,
    last_message_at VARCHAR(40) NOT NULL DEFAULT '',
    created_at      VARCHAR(40) NOT NULL,
    CONSTRAINT fk_conv_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_inquirer FOREIGN KEY (inquirer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conv_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uniq_conv_listing_inquirer (listing_id, inquirer_id),
    INDEX idx_conversations_inquirer (inquirer_id),
    INDEX idx_conversations_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    sender_id       VARCHAR(36) NOT NULL,
    body            TEXT        NOT NULL,
    read_at         VARCHAR(40) NOT NULL DEFAULT '',
    created_at      VARCHAR(40) NOT NULL,
    CONSTRAINT fk_messages_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_messages_conversation (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Viewing appointments: 57% of respondents wanted in-app scheduling, and
-- "scheduling viewings" was the #4 cause of transaction delays.
CREATE TABLE IF NOT EXISTS viewing_requests (
    id            VARCHAR(36) NOT NULL PRIMARY KEY,
    listing_id    VARCHAR(36) NOT NULL,
    requester_id  VARCHAR(36) NOT NULL,
    owner_id      VARCHAR(36) NOT NULL,
    scheduled_for VARCHAR(40) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'requested',
    note          TEXT        NULL,
    response_note TEXT        NULL,
    created_at    VARCHAR(40) NOT NULL,
    updated_at    VARCHAR(40) NOT NULL,
    CONSTRAINT fk_viewings_listing FOREIGN KEY (listing_id) REFERENCES listings(id) ON DELETE CASCADE,
    CONSTRAINT fk_viewings_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_viewings_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_viewings_requester (requester_id),
    INDEX idx_viewings_owner (owner_id),
    INDEX idx_viewings_listing (listing_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agent / owner ratings: 39% of respondents asked for ratings & reviews.
CREATE TABLE IF NOT EXISTS reviews (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    subject_user_id VARCHAR(36) NOT NULL,
    author_id       VARCHAR(36) NOT NULL,
    listing_id      VARCHAR(36) NOT NULL DEFAULT '',
    rating          INT         NOT NULL,
    comment         TEXT        NULL,
    created_at      VARCHAR(40) NOT NULL,
    CONSTRAINT fk_reviews_subject FOREIGN KEY (subject_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uniq_review_subject_author (subject_user_id, author_id),
    INDEX idx_reviews_subject (subject_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Saved searches with alerting, for the "instant alerts / saved searches"
-- requests in the free-text answers.
CREATE TABLE IF NOT EXISTS saved_searches (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id         VARCHAR(36)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    query_json      TEXT         NOT NULL,
    alerts_enabled  TINYINT(1)   NOT NULL DEFAULT 1,
    last_alerted_at VARCHAR(40)  NOT NULL DEFAULT '',
    created_at      VARCHAR(40)  NOT NULL,
    CONSTRAINT fk_saved_searches_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_saved_searches_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One-time codes for phone verification. A verified badge is meaningless if the
-- contact number behind it was never proven, so this closes that hole.
CREATE TABLE IF NOT EXISTS phone_verifications (
    user_id     VARCHAR(36)  NOT NULL PRIMARY KEY,
    phone       VARCHAR(32)  NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    sends       INT          NOT NULL DEFAULT 0,
    expires_at  VARCHAR(40)  NOT NULL,
    last_sent_at VARCHAR(40) NOT NULL,
    window_started_at VARCHAR(40) NOT NULL,
    CONSTRAINT fk_phoneverif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
