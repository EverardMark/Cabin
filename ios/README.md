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

## Structure

```
Cabin/
  App/            CabinApp (entry) + RootView (auth gate + tabs)
  State/          AppState (@Observable) — auth + API access
  Models/         Codable models (snake_case ↔ camelCase)
  Networking/     APIClient (async), SessionStore + Keychain
  Views/          Auth, Listings (+ card), Detail, Create, Profile, shared Components
  Support/        Config (base URL), Theme (colors), Format (money/urls)
```

## Screens

Auth (login/register) · Browse with search & filters · Listing detail with paged gallery ·
Post a listing (PhotosPicker upload) · Profile with your listings and logout.
