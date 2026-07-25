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

## Architecture

```
cmd/api/main.go            entrypoint + graceful shutdown
internal/
  config/                  env configuration
  database/                SQLite open + embedded schema migration
  models/                  domain types
  auth/                    bcrypt password hashing + JWT
  middleware/              CORS, logging, panic recovery
  store/                   data access (users, listings, images)
  storage/                 local image file storage
  handlers/                HTTP handlers + routing + validation
  seed/                    demo data
```

## Endpoints

Base path `/api/v1`. Send `Authorization: Bearer <token>` for authenticated routes.

| Method | Path | Auth | Notes |
|---|---|:--:|---|
| `GET` | `/health` | | Liveness check |
| `POST` | `/api/v1/auth/register` | | `{email, password, name}` → `{token, user}` |
| `POST` | `/api/v1/auth/login` | | `{email, password}` → `{token, user}` |
| `GET` | `/api/v1/auth/me` | ✓ | Current user |
| `GET` | `/api/v1/listings` | | Query: `q, city, property_type, listing_type, min_price, max_price, min_bedrooms, status, sort, page, page_size` |
| `GET` | `/api/v1/listings/{id}` | | Detail with images + owner |
| `POST` | `/api/v1/listings` | ✓ | Create |
| `PUT` | `/api/v1/listings/{id}` | ✓ | Update (owner only) |
| `DELETE` | `/api/v1/listings/{id}` | ✓ | Delete (owner only) |
| `POST` | `/api/v1/listings/{id}/images` | ✓ | `multipart/form-data`, field `image` |
| `GET` | `/api/v1/me/listings` | ✓ | Caller's listings |
| `GET` | `/uploads/{file}` | | Serves uploaded images |

`property_type`: house, apartment, condo, townhouse, land · `listing_type`: sale, rent ·
`sort`: recent (default), oldest, price_asc, price_desc · `price` is in whole currency units.

### Quick curl

```bash
curl -s localhost:8080/api/v1/listings | jq '.total'

TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@cabin.app","password":"password123"}' | jq -r .token)

curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN" | jq
```

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
storage/a CDN, and serve behind TLS.
