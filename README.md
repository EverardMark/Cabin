# 🏡 Cabin — Real Estate Posting Platform

A full-stack real estate marketplace for the Philippines: browse, search, and post
properties for sale or rent, with automated screening so buyers can tell a real
listing from a scam. Three projects share one backend:

| Project | Stack | Path |
|---|---|---|
| **Server** | Go 1.25 · `net/http` · SQLite/MySQL · JWT · Claude | [`server/`](server/) |
| **iOS** | Swift · SwiftUI · Observation · MapKit · async/await | [`ios/`](ios/) |
| **Android** | Kotlin · Jetpack Compose · Retrofit · Maps Compose · Coil | [`android/`](android/) |

```
┌─────────────┐     ┌─────────────┐
│   iOS app   │     │ Android app │
│  (SwiftUI)  │     │  (Compose)  │
└──────┬──────┘     └──────┬──────┘
       │   HTTP + JWT      │
       └─────────┬─────────┘
                 ▼
        ┌──────────────────┐        ┌──────────────────┐
        │   Go REST API    │───────▶│  Claude review   │
        │  /api/v1/...     │        │ (listing + user) │
        └────────┬─────────┘        └──────────────────┘
                 ▼
        ┌──────────────────┐
        │ SQLite + uploads │
        └──────────────────┘
```

## Why the app is shaped this way

The feature set is driven by a **56-response user survey** run in January 2026 across
Muntinlupa and the surrounding Laguna / Cavite / Metro Manila corridor. The headline
findings, and what each one became:

| Survey finding | What we built |
|---|---|
| **86%** rate trust & verification "extremely important"; **79%** want verified-only listings | Every listing is screened before it earns a badge; "Verified only" is a filter that **defaults to on** |
| **57%** have hit a scam or misleading listing | One-tap reporting, a moderation queue, and auto re-screening after 3 reports |
| **54%** cite fake or low-quality listings as their biggest problem | Automated review that rejects scam patterns and flags thin listings |
| **36%** are owners/sellers; **nobody** wanted an agents-only market | **Anyone signed in can post** — the old agent-only gate is gone |
| **59%** want in-app chat | Per-listing threads, with a standing "never pay before viewing" warning |
| **57%** want viewing scheduling | Request → accept/decline → complete, with double-booking checks |
| **48%** want map search | Map screens on both clients, backed by a bounding-box query |
| **46%** want price comparison | "Price check" card comparing against similar nearby listings |
| **39%** want agent ratings & reviews | Reviews you can only write after **completing a viewing** with someone |
| "Outdated listings" (recurring free-text complaint) | Freshness tracking, a stale warning, and a one-tap "still available" confirm |
| Saved searches / instant alerts (free-text) | Saved searches with a new-match count |
| **Featured listings** — 6/6 agents, 58% of owners | Paid promotion, sold per listing |

## Features

- 🔐 **Auth** — register / login with JWT and Google sign-in; tokens in Keychain (iOS) and DataStore (Android)
- ✅ **Automated verification** — every listing is screened by Claude before it earns a badge; accounts prove their mobile number by SMS, then request identity verification
- 🚩 **Reporting & moderation** — report a listing, admin queue, automatic re-screening
- 🔎 **Browse & search** — full-text search, buy/rent and property-type filters, verified-only and freshness filters, sorting
- 🗺️ **Map search** — pan the map to search a viewport
- 💬 **In-app chat** — per-listing threads with unread counts
- 📅 **Viewings** — request, accept, reschedule, complete
- ⭐ **Reviews** — earned by completing a viewing, not open to anyone
- 📊 **Price check** — how a listing compares to similar nearby ones
- 🔔 **Saved searches** — re-run a filter and see what's new
- ⭐ **Featured listings** — paid promotion that boosts placement, never credibility
- ➕ **Posting for everyone** — owners, agents and renters can all post, with a live quality checklist
- ✏️ **Full listing management** — edit, delete, set a map pin, and add/remove/reorder photos
- 🌱 **Seed data** — demo accounts and Philippine sample listings on first run

---

## Quick start

### 1. Run the server first (all clients talk to it)

```bash
cd server && make run
```

The API starts on **http://localhost:8080**, creates a local SQLite database, and seeds
demo data.

**Demo accounts** (all use password `password123`):

| Email | Role | Why it's there |
|---|---|---|
| `demo@cabin.app` | Licensed agent, verified | The professional path |
| `owner@cabin.app` | Private owner, verified | Proves owners can post too |
| `admin@cabin.app` | Admin | Moderation queue at `/api/v1/admin/reports` |

Verify it's up:

```bash
curl http://localhost:8080/health
```

The response tells you whether AI review is active:
`{"ai_review":true,"review_model":"claude-opus-5",...}`

### 2. Turn on Claude-backed screening (optional but recommended)

Without a key the server still screens every listing, but with a **rule-based
fallback** that only checks completeness — it can flag a thin listing, and it will
never catch an actual scam. With a key, Claude reads each listing and rejects
scam patterns (upfront-payment demands, off-platform contact, implausible prices).

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Screening runs off the request path in a background worker, so posting stays fast.
Each review is one short Claude call at `medium` effort; the system prompt is cached
across reviews. See [`server/README.md`](server/README.md) for cost notes and how to
change the model with `VERIFY_MODEL`.

### 3. iOS

```bash
cd ios && xcodegen generate && open Cabin.xcodeproj
```

Press **Run** in Xcode (any iOS 17+ simulator). The simulator reaches the server at
`localhost` automatically. Needs [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`).

### 4. Android

```bash
cd android
# Open this folder in Android Studio, let it sync, then Run on an emulator.
```

The Android emulator reaches your computer's `localhost` via `10.0.2.2`, which is
already configured as the default API base URL.

> **Physical devices:** replace the base URL with your Mac's LAN IP
> (e.g. `http://192.168.1.20:8080`) in `ios/Cabin/Support/Config.swift` and
> `android/app/build.gradle.kts` (`API_BASE_URL`).

### Optional keys

Both are left blank by default, and each feature stays dormant until you fill it in:

| Key | Where | Enables |
|---|---|---|
| `ANTHROPIC_API_KEY` | server env | Claude listing/account review (falls back to rules) |
| `GOOGLE_CLIENT_ID` | server env | Google sign-in (`/auth/google` returns 501 without it) |
| `GIDClientID` | `ios/project.yml` | Google sign-in button on iOS |
| `GOOGLE_WEB_CLIENT_ID` | `android/app/build.gradle.kts` | Google sign-in on Android |
| `MAPS_API_KEY` | `android/app/build.gradle.kts` | Android map screen (iOS uses MapKit, no key needed) |
| `SMS_URL` / `SMS_API_KEY` | server env | Real SMS delivery for phone codes (otherwise codes go to the log) |

---

## API reference

Base path: `/api/v1`. Authenticated endpoints require `Authorization: Bearer <token>`.

### Auth & profile

| Method | Path | Auth | Description |
|---|---|:--:|---|
| `POST` | `/auth/register` | | Create account → `{ token, user }` |
| `POST` | `/auth/login` | | Log in → `{ token, user }` |
| `POST` | `/auth/google` | | Exchange a Google ID token → `{ token, user }` (501 if unconfigured) |
| `GET` | `/auth/me` | ✓ | Current user |
| `PATCH` | `/me` | ✓ | Update your profile |
| `POST` | `/me/phone/send-code` | ✓ | Text a one-time code to confirm your number |
| `POST` | `/me/phone/verify` | ✓ | Confirm the code → sets `phone_verified` |
| `POST` | `/me/verification` | ✓ | Submit your account for identity review (needs a confirmed number) |
| `GET` | `/me/summary` | ✓ | Badge counts (unread, viewings, listings needing attention) |
| `GET` | `/users/{id}` | | Public profile |

### Listings

| Method | Path | Auth | Description |
|---|---|:--:|---|
| `GET` | `/listings` | | List/search — see query parameters below |
| `GET` | `/listings/{id}` | | Listing detail (images, owner, verification) |
| `POST` | `/listings` | ✓ | Create a listing (any signed-in account) |
| `PUT` | `/listings/{id}` | ✓ | Update (owner only; content edits trigger re-screening) |
| `DELETE` | `/listings/{id}` | ✓ | Delete (owner only) |
| `POST` | `/listings/{id}/images` | ✓ | Upload an image (multipart, field `image`, max 10MB) |
| `DELETE` | `/listings/{id}/images/{imageId}` | ✓ | Remove a photo (re-triggers screening) |
| `PUT` | `/listings/{id}/images/order` | ✓ | Reorder photos — the first is the thumbnail |
| `POST` | `/listings/{id}/confirm` | ✓ | Confirm still available (clears the stale warning) |
| `POST` | `/listings/{id}/report` | ✓ | Report a scam or misleading listing |
| `GET` | `/listings/{id}/price-comparison` | | Price vs. similar nearby listings |
| `GET` | `/feature-plans` | | Promotion packages a poster can buy |
| `POST` | `/listings/{id}/feature` | ✓ | Buy promoted placement (verified listings only) |
| `GET` | `/me/listings` | ✓ | Your listings (including rejected ones) |

**Browse query parameters:** `q`, `city`, `property_type`, `listing_type`, `status`,
`min_price`, `max_price`, `min_bedrooms`, `min_bathrooms`, `min_area`,
`verified_only`, `exclude_stale`, `sort` (`recent` · `oldest` · `price_asc` ·
`price_desc` · `trusted` · `distance`), `page`, `page_size`, and for map search
`min_lat`/`max_lat`/`min_lng`/`max_lng` or `lat`/`lng`/`radius_km`.

Rejected listings are hidden from ordinary browsing by default.

### Messaging, viewings & reviews

| Method | Path | Auth | Description |
|---|---|:--:|---|
| `POST` | `/listings/{id}/conversations` | ✓ | Open (or reuse) a thread with the poster |
| `GET` | `/conversations` | ✓ | Your threads, with unread counts |
| `GET` | `/conversations/{id}/messages` | ✓ | Messages (also marks them read) |
| `POST` | `/conversations/{id}/messages` | ✓ | Send a message |
| `POST` | `/listings/{id}/viewings` | ✓ | Request a viewing |
| `GET` | `/viewings` | ✓ | Your viewings, both directions |
| `PATCH` | `/viewings/{id}` | ✓ | Accept / decline / cancel / complete / reschedule |
| `GET` | `/users/{id}/reviews` | | Reviews about a user |
| `POST` | `/users/{id}/reviews` | ✓ | Leave a review (requires a completed viewing) |

### Saved searches & moderation

| Method | Path | Auth | Description |
|---|---|:--:|---|
| `GET` | `/me/searches` | ✓ | Saved searches with new-match counts |
| `POST` | `/me/searches` | ✓ | Save the current filters |
| `DELETE` | `/me/searches/{id}` | ✓ | Delete a saved search |
| `GET` | `/me/searches/{id}/results` | ✓ | Re-run a saved search |
| `GET` | `/admin/reports` | admin | Moderation queue |
| `POST` | `/admin/reports/{id}/resolve` | admin | Uphold or dismiss a report |
| `POST` | `/admin/listings/{id}/reverify` | admin | Send a listing back for screening |

Uploaded images are served from `/uploads/<file>`. Full API details:
[`server/README.md`](server/README.md).

---

## Monetization

Promotion is the one thing the survey was unanimous about on the supply side:
**every agent (6/6) and 58% of owners** picked "featured listings" as what they would
pay for. It is sold **per listing**, not per month, because most posters here have a
single property — acceptable spend clustered at ₱500–1,000 for owners and ₱1,000–3,000
for agents.

Starting packages (in `models.FeaturePlans`, to validate against real conversions):
₱299 / 7 days · ₱499 / 14 days · ₱899 / 30 days.

**The rule that makes this safe: paying buys reach, never credibility.**

- Only a **verified** listing can be promoted — `POST /listings/{id}/feature` returns 409
  otherwise.
- If a promoted listing later loses verification (an edit, or three reports triggering
  re-screening) it **silently loses its placement** while its paid time keeps running.
- The "Featured" badge is styled distinctly from the verification badge, and the plan
  picker says so, so nobody reads promotion as a trust signal.

We deliberately **do not sell the verified badge**, even though 46% of buyers said they'd
pay for one. If verified meant *paid*, unverified would mean *didn't pay* rather than
*didn't pass* — and the trust signal this whole app is built on would be worth nothing.

**No payment gateway is wired up yet.** In development the endpoint grants promotion
without charging and reports `"paid": false` so the clients say so plainly. In
production it returns 501 until `PAYMENT_PROVIDER` is set, so promotion can never be
given away by accident.

## Two design decisions worth knowing

**Editing is the repair path.** Screening tells an owner exactly why a listing was
flagged, so the app has to let them act on it. Editing reviewed content — or removing a
photo, since photo count feeds the review — sends the listing back for a fresh check, so
a badge can never be inherited by different content.

**Phone numbers are proven, not typed.** Account verification requires a mobile number
confirmed by SMS. Codes are hashed at rest, expire in 10 minutes, allow 5 attempts and 5
sends a day, and changing your number drops the proof. Without `SMS_URL` the code is
written to the server log and echoed in the API response so the flow works locally —
production refuses to run against the simulated sender rather than pretending.

**Viewings auto-complete 24 hours after their slot.** Completing a viewing is what
unlocks reviews. Leaving that solely with the listing owner gave a badly-behaved owner a
one-tap way to block a review of themselves, so time closes the loop instead. A
background sweep promotes `confirmed` viewings past their grace period; cancelled and
declined ones are never touched.

## What the verified badge does and doesn't mean

The badge means a listing was **screened for scam and quality signals** and passed. It
is deliberately *not* presented as proof of ownership, and the app says so on every
listing detail screen. Three states are visible to buyers:

- **Verified** — passed screening, score 80–100
- **Check details** — usable, but with specific concerns shown (score 40–79)
- **Failed review** — hidden from browsing entirely (score 0–39)

Account verification is separate and covers the poster, not the property. Being an
"agent" is self-declared at signup, so the agent label is an occupation tag only —
never a trust signal.

## Project docs

- [Server README](server/README.md) — endpoints, verification config, moving to MySQL
- [iOS README](ios/README.md) — structure, running, requirements
- [Android README](android/README.md) — structure, running, requirements

## Notes

- The server uses **SQLite** so it runs with zero setup. The data layer uses standard
  `database/sql`; see the server README for switching to MySQL.
- Run the test suite with `cd server && make test`.
- Development conveniences (CORS `*`, cleartext HTTP to localhost, demo seed, an
  insecure default JWT secret) are for local development only — see each README for
  production hardening.
