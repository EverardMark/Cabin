# Cabin API (Go)

REST API for the Cabin real estate platform. Go standard library `net/http` (1.22+ routing),
SQLite storage (pure-Go driver, no CGO), JWT auth, and local image uploads.

## Requirements

- Go 1.24+ (the module toolchain will fetch a newer Go automatically if needed)

No database server needed — SQLite is embedded.

## Run

```bash
make run          # or: go run ./cmd/api
```

- Starts on `http://localhost:8080`
- Creates `cabin.db` (SQLite) on first run
- Seeds a demo account and sample listings when the database is empty

**Demo account:** `demo@cabin.app` / `password123`

```bash
make build        # build ./bin/cabin
make test         # run tests
make clean        # remove binary, db, and uploads
```

## Configuration

All via environment variables (see [`.env.example`](.env.example)); a `.env` file is **not**
auto-loaded — export them or use your process manager.

| Var | Default | Notes |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DB_DRIVER` | `sqlite` | `sqlite` (zero setup) or `mysql` |
| `DB_PATH` | `cabin.db` | SQLite file path (when `DB_DRIVER=sqlite`) |
| `MYSQL_HOST`/`PORT`/`USER`/`PASSWORD`/`DATABASE` | `127.0.0.1`/`3306`/`cabin`/–/`cabin` | MySQL connection (when `DB_DRIVER=mysql`); or set a full `MYSQL_DSN` |
| `JWT_SECRET` | dev secret | **Required in production** (`openssl rand -hex 32`) |
| `UPLOAD_DIR` | `uploads` | Where uploaded images are stored |
| `ENV` | `development` | `production` disables the demo seed & requires `JWT_SECRET` |
| `SEED` | `true` in dev | Seed demo data when the DB is empty |
| `ANTHROPIC_API_KEY` | – | Enables Claude-backed screening; empty falls back to the built-in rule check |
| `VERIFY_MODEL` | `claude-opus-5` | Model used for listing and account review |
| `MAX_UPLOAD_MB` | `10` | Hard cap on a single image upload |
| `GOOGLE_CLIENT_ID` | – | Comma-separated accepted Google client IDs (Web, iOS, Android) |

## Architecture

```
cmd/api/main.go            entrypoint + graceful shutdown
internal/
  config/                  env configuration
  database/                SQLite open + embedded schema migration
  models/                  domain types
  auth/                    bcrypt password hashing + JWT
  middleware/              CORS, logging, panic recovery
  store/                   data access (users, listings, chat, viewings, reviews, searches)
  storage/                 local image file storage
  verify/                  listing & account screening (Claude, with a rule-based fallback)
  handlers/                HTTP handlers + routing + validation
  seed/                    demo data
```

## Endpoints

Base path `/api/v1`. Send `Authorization: Bearer <token>` for authenticated routes.

| Method | Path | Auth | Notes |
|---|---|:--:|---|
| `GET` | `/health` | | Liveness check; reports the active reviewer |
| `POST` | `/auth/register` | | `{email, password, name, phone?, role?}` → `{token, user}` |
| `POST` | `/auth/login` | | `{email, password}` → `{token, user}` |
| `POST` | `/auth/google` | | `{id_token}` → `{token, user}`; 501 until `GOOGLE_CLIENT_ID` is set |
| `GET` | `/auth/me` | ✓ | Current user |
| `PATCH` | `/me` | ✓ | Update name, phone, bio, licence, role |
| `POST` | `/me/verification` | ✓ | Submit the account for identity review |
| `GET` | `/me/summary` | ✓ | Unread messages, viewings, listings needing attention |
| `GET` | `/me/listings` | ✓ | Caller's listings, including rejected ones |
| `GET` | `/users/{id}` | | Public profile |
| `GET` | `/listings` | | See query parameters below |
| `GET` | `/listings/{id}` | | Detail with images, owner, verification |
| `POST` | `/listings` | ✓ | Create (any signed-in account) |
| `PUT` | `/listings/{id}` | ✓ | Update (owner only; content edits re-trigger screening) |
| `DELETE` | `/listings/{id}` | ✓ | Delete (owner only) |
| `POST` | `/listings/{id}/images` | ✓ | `multipart/form-data`, field `image`; capped by `MAX_UPLOAD_MB` |
| `POST` | `/listings/{id}/confirm` | ✓ | Owner confirms still available |
| `POST` | `/listings/{id}/report` | ✓ | `{reason, details?}`; 3 open reports force re-screening |
| `GET` | `/listings/{id}/price-comparison` | | Median/min/max vs. similar nearby listings |
| `POST` | `/listings/{id}/conversations` | ✓ | Open or reuse a chat thread |
| `GET` | `/conversations` | ✓ | Threads with unread counts |
| `GET` | `/conversations/{id}/messages` | ✓ | Messages; also marks them read |
| `POST` | `/conversations/{id}/messages` | ✓ | `{body}` |
| `POST` | `/listings/{id}/viewings` | ✓ | `{scheduled_for, note?}` (RFC3339) |
| `GET` | `/viewings` | ✓ | Viewings on both sides |
| `PATCH` | `/viewings/{id}` | ✓ | `{status}` or `{scheduled_for}` to propose a new time |
| `GET` | `/users/{id}/reviews` | | Reviews about a user |
| `POST` | `/users/{id}/reviews` | ✓ | `{rating, comment?}`; requires a completed viewing |
| `GET` | `/me/searches` | ✓ | Saved searches with new-match counts |
| `POST` | `/me/searches` | ✓ | `{name, query}` |
| `DELETE` | `/me/searches/{id}` | ✓ | Delete |
| `GET` | `/me/searches/{id}/results` | ✓ | Re-run and clear the "new" badge |
| `GET` | `/admin/reports` | admin | Moderation queue |
| `POST` | `/admin/reports/{id}/resolve` | admin | `{status: upheld\|dismissed, resolution?}` |
| `POST` | `/admin/listings/{id}/reverify` | admin | Force re-screening |
| `GET` | `/uploads/{file}` | | Serves one uploaded image (no directory index) |

**Browse query parameters:** `q, city, property_type, listing_type, status, min_price,
max_price, min_bedrooms, min_bathrooms, min_area, verified_only, exclude_stale, sort,
page, page_size`, plus map search via `min_lat/max_lat/min_lng/max_lng` or
`lat/lng/radius_km`.

`property_type`: house, apartment, condo, townhouse, land · `listing_type`: sale, rent ·
`sort`: recent (default), oldest, price_asc, price_desc, trusted, distance ·
`role`: user, agent (admin is provisioned, never self-registered) · `price` is in whole
currency units. Rejected listings are excluded from browsing unless you ask for them.

### Quick curl

```bash
curl -s localhost:8080/api/v1/listings | jq '.total'

TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@cabin.app","password":"password123"}' | jq -r .token)

curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN" | jq

# Only verified listings — the survey's most requested filter
curl -s 'localhost:8080/api/v1/listings?verified_only=true' | jq '.total'

# What the reviewer concluded about each listing
curl -s localhost:8080/api/v1/listings | \
  jq -r '.listings[] | "\(.verification_status)\t\(.verification_score)\t\(.title)"'
```

## Listing verification

Every listing is screened before it can earn a verified badge. This is the app's answer
to the user survey's dominant finding: 86% of respondents called trust and verification
"extremely important", and 79% asked to see verified listings only.

**How it runs.** `POST /listings` stores the listing as `pending` and returns immediately;
a background worker (`internal/verify`) picks it up, reviews it, and writes back a status,
a 0–100 trust score, a one-sentence summary shown to buyers, and machine-readable concern
flags. Editing reviewed content, adding a photo, or accumulating three open reports sends
a listing back through review, so a badge can never be inherited by different content.

**Statuses.** `pending` → `verified` (80–100) · `flagged` (40–79, browsable with warnings)
· `rejected` (0–39, hidden from ordinary browsing).

**Two reviewers.**

- **Claude** (when `ANTHROPIC_API_KEY` is set) reads the listing and judges scam signals:
  upfront-payment demands, pushes to contact off-platform, implausible pricing, internal
  inconsistency, urgency pressure. Listing content is fenced in the prompt and treated as
  untrusted data, and a listing that tries to instruct the reviewer is itself a rejection
  signal.
- **The rule-based fallback** (no key) checks completeness only — photo count, description
  length, location, price, property details. It can flag, but it deliberately **never
  rejects**: rules cannot tell a terse honest listing from a scam, and wrongly hiding a
  real listing is the worse error.

`GET /health` reports which reviewer is active. Accounts get the same treatment through
`POST /me/verification`, which weighs profile completeness, a contact number, and — for
accounts claiming to be agents — a licence number.

**Cost.** One short Claude call per listing at `medium` effort, with the system prompt
cached across reviews. Set `VERIFY_MODEL` to use a cheaper model if volume grows.

## Database backends (SQLite / MySQL)

The store layer uses standard `database/sql` with `?` placeholders, which both drivers share,
so switching backends is just configuration:

- **SQLite** (default) — zero setup, file-based, great for local dev.
- **MySQL 8** — set `DB_DRIVER=mysql` and the `MYSQL_*` vars (or `MYSQL_DSN`).

Each backend has its own idempotent schema (`internal/database/schema_sqlite.sql` /
`schema_mysql.sql`), applied automatically on startup. The models, handlers, and API contract are
identical across both.

> The production server (EC2) runs **MySQL 8**; MySQL must be reachable before the API starts
> (the app retries the connection for ~15s on boot).

## Production notes

This build favors local-dev convenience. Before shipping: set a strong `JWT_SECRET`, set
`ENV=production`, restrict CORS (currently `*`) in `internal/middleware`, put uploads on object
storage/a CDN, serve behind TLS, and **add rate limiting to the auth endpoints** (there is
none today, so login is brute-forceable).

Run the tests with `make test`.
