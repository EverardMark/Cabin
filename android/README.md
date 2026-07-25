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
    auth/ listings/ detail/ create/ profile/
  util/                    formatting, error mapping, media
```

## Screens

Auth (login/register) · Browse with search & filters · Listing detail with image pager ·
Post a listing (with multi-photo picker) · Profile with your listings and logout.
