# Cabin (iOS)

Native iOS client for Cabin, built with SwiftUI and the Observation framework.

## Requirements

- Xcode 15+ (developed on Xcode 26)
- iOS 17+ simulator or device
- [XcodeGen](https://github.com/yonyz/XcodeGen) to generate the project: `brew install xcodegen`

## Run

1. Start the [server](../server/) (`cd server && make run`).
2. Generate and open the project:
   ```bash
   cd ios
   xcodegen generate
   open Cabin.xcodeproj
   ```
3. Select an iOS 17+ simulator and press **Run**. The simulator shares the Mac's network,
   so it reaches the server at `localhost` automatically.

**Demo account:** `demo@cabin.app` / `password123` (there's a one-tap "Continue as demo" button).

> The Xcode project is generated from [`project.yml`](project.yml) and is git-ignored — run
> `xcodegen generate` after cloning or changing the spec.
>
> **Physical device:** set `baseURL` in [`Cabin/Support/Config.swift`](Cabin/Support/Config.swift)
> to your Mac's LAN IP (e.g. `http://192.168.1.20:8080`).

## Stack

- **SwiftUI** + `NavigationStack`, `TabView`
- **Observation** (`@Observable` / `@Environment`) for app state
- **async/await** networking over `URLSession`
- **Keychain** for the JWT, `UserDefaults` for the cached user
- **PhotosPicker** for multi-image upload
- **MapKit** for map search (no API key needed)

## Structure

```
Cabin/
  App/            CabinApp (entry) + RootView (auth gate + tabs)
  State/          AppState (@Observable) — auth + API access
  Models/         Codable models (snake_case ↔ camelCase)
  Networking/     APIClient (async), SessionStore + Keychain
  Views/          Auth, Listings, Detail, Map, Messages, Viewings, UserProfile,
                  SavedSearches, Create, Profile, TrustBadges, shared Components
  Support/        Config (base URL), Theme (colors), Format (money/dates/urls)
```

## Screens

Five tabs — **Browse**, **Messages**, **Post**, **Viewings**, **Profile** — plus pushed
screens for listing detail, map search, chat, another user's profile, and saved searches.

- **Browse** — search, buy/rent and property-type filters, sort, and a **"Verified listings
  only"** switch that defaults to on (the survey's most requested feature, at 79%)
- **Listing detail** — paged gallery, a trust panel showing the screening verdict and its
  concerns, a stale-listing warning, a price check against similar listings, the poster's
  verification and rating, and buttons to message, book a viewing, or report
- **Map** — pan to search a viewport; verified listings get a distinct pin
- **Messages / Chat** — per-listing threads with unread counts and a standing warning never
  to pay before viewing
- **Viewings** — request, accept, decline, cancel, complete
- **Profile** — your verification status and a "Request verification" button, saved searches,
  viewings, and your own listings annotated with why any were flagged or rejected

## Trust UI

`Views/TrustBadges.swift` holds the verification badge, trust panel, rating stars and stale
warning. The badge deliberately claims a *screened listing*, not proof of ownership, and the
detail screen says so. The "Agent" label is an occupation tag only — it is self-declared at
signup, so it is never presented as a trust signal.
