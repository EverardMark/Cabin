# 🏡 Cabin — Real Estate Posting Platform

A full-stack real estate listings app: browse, search, and post properties for sale or rent.
Three projects share one backend:

| Project | Stack | Path |
|---|---|---|
| **Server** | Go 1.24 · `net/http` · SQLite · JWT | [`server/`](server/) |
| **iOS** | Swift · SwiftUI · Observation · async/await | [`ios/`](ios/) |
| **Android** | Kotlin · Jetpack Compose · Retrofit · Coil | [`android/`](android/) |

```
┌─────────────┐     ┌─────────────┐
│   iOS app   │     │ Android app │
│  (SwiftUI)  │     │  (Compose)  │
└──────┬──────┘     └──────┬──────┘
       │   HTTP + JWT      │
       └─────────┬─────────┘
                 ▼
        ┌──────────────────┐
        │   Go REST API    │
        │  /api/v1/...     │
        └────────┬─────────┘
                 ▼
        ┌──────────────────┐
        │ SQLite + uploads │
        └──────────────────┘
```

## Features

- 🔐 **Auth** — register / login with JWT, tokens stored in Keychain (iOS) and DataStore (Android)
- 🔎 **Browse & search** — full-text search, filter by buy/rent and property type, sortable
- 🏠 **Listing details** — image gallery, price, beds/baths/area, description, agent card
- ➕ **Post a listing** — full form with multi-photo upload from the device
- 👤 **Profile** — your own listings and logout
- 🌱 **Seed data** — a demo account and sample listings are created on first run

---

## Quick start

### 1. Run the server first (all clients talk to it)

```bash
cd server
make run
```

The API starts on **http://localhost:8080**, creates a local SQLite database, and seeds demo data.

**Demo account:** `demo@cabin.app` / `password123`

Verify it's up:

```bash
curl http://localhost:8080/health
```

### 2. iOS

```bash
cd ios
xcodegen generate      # generates Cabin.xcodeproj from project.yml
open Cabin.xcodeproj
```

Press **Run** in Xcode (any iOS 17+ simulator). The simulator reaches the server at `localhost` automatically.

### 3. Android

```bash
cd android
# Open this folder in Android Studio, let it sync, then Run on an emulator.
```

The Android emulator reaches your computer's `localhost` via `10.0.2.2`, which is already configured
as the default API base URL.

> **Physical devices:** replace the base URL with your Mac's LAN IP
> (e.g. `http://192.168.1.20:8080`) in `ios/Cabin/Support/Config.swift` and
> `android/app/build.gradle.kts` (`API_BASE_URL`).

---

## API reference

Base path: `/api/v1`. Authenticated endpoints require `Authorization: Bearer <token>`.

| Method | Path | Auth | Description |
|---|---|:--:|---|
| `POST` | `/auth/register` | | Create account → `{ token, user }` |
| `POST` | `/auth/login` | | Log in → `{ token, user }` |
| `GET` | `/auth/me` | ✓ | Current user |
| `GET` | `/listings` | | List/search (`q`, `city`, `property_type`, `listing_type`, `min_price`, `max_price`, `min_bedrooms`, `sort`, `page`, `page_size`) |
| `GET` | `/listings/{id}` | | Listing detail (with images + owner) |
| `POST` | `/listings` | ✓ | Create a listing |
| `PUT` | `/listings/{id}` | ✓ | Update (owner only) |
| `DELETE` | `/listings/{id}` | ✓ | Delete (owner only) |
| `POST` | `/listings/{id}/images` | ✓ | Upload an image (multipart, field `image`) |
| `GET` | `/me/listings` | ✓ | Your listings |

Uploaded images are served from `/uploads/...`. Full API details: [`server/README.md`](server/README.md).

---

## Project docs

- [Server README](server/README.md) — endpoints, config, moving to PostgreSQL
- [iOS README](ios/README.md) — structure, running, requirements
- [Android README](android/README.md) — structure, running, requirements

## Notes

- The server uses **SQLite** so it runs with zero setup. The data layer uses standard
  `database/sql`; see the server README for switching to PostgreSQL.
- Development conveniences (CORS `*`, cleartext HTTP to localhost, demo seed, an insecure
  default JWT secret) are for local development only — see each README for production hardening.
