# Cabin (Android)

Native Android client for Cabin, built with Kotlin and Jetpack Compose (Material 3).

## Requirements

- Android Studio (Ladybug or newer)
- Android SDK 35, minSdk 26
- JDK 17+ (Android Studio's bundled JBR works)

## Run

1. Start the [server](../server/) (`cd server && make run`).
2. Open the `android/` folder in Android Studio and let Gradle sync.
3. Run on an emulator (▶). The app defaults to `http://10.0.2.2:8080/`, which is the
   emulator's alias for your computer's `localhost`.

Or from the command line:

```bash
./gradlew assembleDebug          # build app/build/outputs/apk/debug/app-debug.apk
# with an emulator/device connected:
./gradlew installDebug
```

**Demo account:** `demo@cabin.app` / `password123` (there's a one-tap "Continue as demo" button).

> **Physical device:** set `API_BASE_URL` in [`app/build.gradle.kts`](app/build.gradle.kts) to your
> computer's LAN IP (e.g. `http://192.168.1.20:8080/`). Cleartext HTTP to that host must be allowed
> in [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml).

## Stack

- **Jetpack Compose** + Material 3, Navigation Compose
- **MVVM** with `ViewModel` + `StateFlow`
- **Retrofit** + OkHttp + kotlinx.serialization (snake_case ↔ camelCase)
- **Coil** for image loading
- **Maps Compose** for map search (needs a `MAPS_API_KEY`; dormant without one)
- **DataStore** for the auth token / cached user
- Manual DI via a small `ServiceLocator`

## Structure

```
app/src/main/java/com/cabin/app/
  CabinApp.kt              Application (initializes ServiceLocator)
  MainActivity.kt          Compose host
  data/
    model/Models.kt        @Serializable DTOs
    remote/                Retrofit API + OkHttp/JSON setup
    SessionStore.kt        token + user via DataStore
    CabinRepository.kt     auth state + API calls (returns Result)
    ServiceLocator.kt
  ui/
    theme/                 Material 3 theme
    CabinRoot.kt           auth gate + bottom-nav scaffold
    common/                shared components + trust badges/panels
    auth/ listings/ detail/ create/ profile/
    map/ messages/ viewings/ userprofile/ searches/
  util/                    formatting, error mapping, media
```

## Screens

Five tabs — **Browse**, **Messages**, **Post**, **Viewings**, **Profile** — plus pushed
screens for listing detail, map search, chat, another user's profile, and saved searches.

- **Browse** — search, filters, sort, and a **"Verified listings only"** switch that defaults
  to on (the survey's most requested feature, at 79%)
- **Listing detail** — image pager, a trust panel showing the screening verdict and its
  concerns, a stale-listing warning, a price check, the poster's verification and rating, and
  actions to message, book a viewing, or report
- **Map** — pan to search a viewport (needs `MAPS_API_KEY`; shows a clear "not configured"
  message without one)
- **Messages / Chat** — per-listing threads with unread badges
- **Viewings** — request, accept, decline, cancel, complete
- **Profile** — verification status and request button, saved searches, viewings, and your own
  listings annotated with why any were flagged or rejected

## Trust UI

`ui/common/TrustViews.kt` holds the verification badge, trust panel, rating stars and stale
warning. The badge claims a *screened listing*, not proof of ownership, and the detail screen
says so. The "AGENT" label is an occupation tag only — self-declared at signup, so it is never
presented as a trust signal.

## Optional keys

Both are blank by default and each feature stays dormant until filled in, in
[`app/build.gradle.kts`](app/build.gradle.kts):

- `GOOGLE_WEB_CLIENT_ID` — Sign in with Google
- `MAPS_API_KEY` — the map search screen (also wired into the manifest as a placeholder)
