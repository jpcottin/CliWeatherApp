# CliWeatherApp

[![Android CI](https://github.com/jpcottin/CliWeatherApp/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jpcottin/CliWeatherApp/actions/workflows/ci.yml)

<details>
<summary><b>CI details</b> — emulator matrix, API 36 → 37.1, plus Android CLI and Emulator Preview legs</summary>

| Legs | Image | Emulator channel | GPU | Gating |
|---|---|---|---|---|
| API 36 | `google_apis` x86_64 | stable | auto | ✅ blocking |
| API 37.0 | `google_apis_ps16k` (16 KB page size) | stable | lavapipe | ✅ blocking (runs MainActivityAPI37Test) |
| API 37.0 | `google_apis_ps16k` | canary (`--channel=3`) | lavapipe, auto | non-blocking |
| API 37.1 | `google_apis_ps16k` | canary | lavapipe, auto | non-blocking |
| Android CLI experiment | `google_apis_ps16k` 37.0 | canary | emulator default | non-blocking |
| Emulator Preview (`emulators;latest`) | `google_apis_ps16k` 37.0 | preview package | auto | non-blocking |
| Emulator Preview multi-run (snapshot cycles) | `google_apis_ps16k` 37.0 | preview package | auto | non-blocking |
| Android CLI multi-run (snapshot cycles) | `google_apis_ps16k` 37.0 | canary | emulator default | non-blocking |

The Android CLI leg drives the whole flow with the [`android` CLI](https://d.android.com/tools/agents/android-cli) (`android sdk install --canary`, `android emulator create/start/stop`) instead of `sdkmanager`/`avdmanager` and the emulator-runner action. It also runs an end-to-end GPS check: it streams `adb emu geo fix` (Tokyo) into the emulator and asserts via `android layout` that the app reverse-geocodes it to Shinjuku City and loads a forecast.

All emulator-runner legs use the `pixel_6` profile, full diagnostics (`-verbose -show-kernel -debug-metrics -metrics-collection`), and a `cmdline-tools;latest` update so `avdmanager` writes a valid `target=android-37.x` (the runner's preinstalled version writes `android-0`, which the emulator clamps to API 3, disabling the Vulkan/GLDirectMem auto-enable the ps16k images need).


Two of the non-blocking jobs run a **snapshot multi-run experiment**: the emulator is booted four
times against the same AVD with quickboot snapshots enabled, the app is launched only on the first
cycle, and every later cycle checks whether the snapshot brought it back by itself — still running,
and still rendering. The app is deliberately never relaunched after a restore, since that is the
thing being measured. One job drives the Emulator Preview package, the other the canary emulator
through the `android` CLI, so the same experiment can be compared across both.

Because this app's UI is static between refreshes, rendering is judged with `android layout` rather
than by diffing two screenshots — a screenshot diff would report a stall on every cycle. A non-empty
layout tree plus a focused window means the UI is present and enumerable. Screenshots are still
captured and uploaded for every cycle. `scripts/replay-preview-multirun.sh` replays the multi-run
job locally in a few minutes instead of a push cycle.

</details>

CliWeatherApp is a robust, modern Android weather application built with Jetpack Compose. It provides real-time weather updates, detailed forecasts, and interactive location tools, all wrapped in a beautiful, responsive Material 3 interface.

## 🌟 Key Features

*   **Comprehensive Weather Data**: Instant access to current temperature, weather conditions, and icons.
*   **Detailed Forecasts**:
    *   **Customizable Hourly Forecast**: Display from **0 to 168 hours** of upcoming temperature, condition, UV index, and AQI data.
    *   **Customizable Daily Forecast**: View from **0 to 16 days** of daily high/low ranges, UV index max, and sunrise/sunset times.
*   **Dual-Mode Location Support**:
    *   **Interactive World Map**: Tap anywhere on the high-precision map to fetch weather for that specific coordinate.
    *   **Real-time GPS**: Automatic tracking of your device's movement. Fully compatible with `adb emu geo fix` for emulator testing.
*   **Text-to-Speech (TTS)**: Listen to your weather report in your preferred language.
*   **Social Sharing**: Capture a snapshot of your current weather and share it instantly with friends.
*   **Multi-Device Optimized**: Native support for Phone, Tablet, and Foldable layouts using `WindowSizeClass`. On phones, the header uses a compact side-by-side layout (time + badges on the left, weather icon and temperature on the right) to maximise vertical space for forecasts.

## ⚙️ App Settings

Tailor the app to your preferences with a comprehensive settings suite:

*   **Temperature Units**: Seamlessly toggle between **Celsius** and **Fahrenheit**.
*   **Time Format**: Choose between **12-hour** (AM/PM) and **24-hour** display formats.
*   **Localization**: Full support for five languages with instant UI translation:
    *   English
    *   Français (French)
    *   Español (Spanish)
    *   Deutsch (German)
    *   日本語 (Japanese)
*   **Forecast Range**: Precisely control the amount of data shown in the hourly (0-168h) and daily (0-16d) forecast rows using intuitive sliders.
*   **Sunrise & Sunset**: Toggle the display of sunrise and sunset times on daily forecast cards (on by default).
*   **UV Index**: Toggle UV index display (off by default). When on: shows a colour-coded badge next to the clock, per-hour UV values in the hourly forecast, and daily UV max in the daily forecast. Announced by TTS in all 5 languages.
*   **Air Quality (AQI)**: Toggle European AQI display (off by default). When on: shows a colour-coded badge next to the clock and per-hour AQI values in the hourly forecast (today only). Uses the Open-Meteo Air Quality API — no API key required.
*   **Location Mode**: Switch between **Map Mode** (fixed point) and **GPS Mode** (continuous tracking).

## 🔒 Permissions

The app requests minimal permissions to ensure user privacy while providing full functionality:

*   `INTERNET`: Required to fetch weather data from the Open-Meteo API and perform reverse geocoding.
*   `ACCESS_FINE_LOCATION`: Required for high-accuracy GPS tracking.
*   `ACCESS_COARSE_LOCATION`: Required for approximate location tracking and compatibility with Android 12+ "Approximate Location" privacy settings.

## 🌐 External API

CliWeatherApp utilizes the **[Open-Meteo API](https://open-meteo.com/)** for all weather and air quality data.
*   **Transparency**: No API keys are required for standard usage.
*   **Query Optimization**: The app dynamically calculates the minimum required number of forecast days (up to 16) based on your selected hourly and daily ranges to minimize network payload and ensure snappier updates.
*   **Weather endpoint** (`api.open-meteo.com/v1/forecast`): fetches `hourly=temperature_2m,weather_code,is_day,uv_index` and `daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max`. UV index (hourly and daily max) and sunrise/sunset are always requested regardless of display settings — zero extra network cost.
*   **Air quality endpoint** (`air-quality-api.open-meteo.com/v1/air-quality`): fetches `hourly=european_aqi` for today (`forecast_days=1`). This call is only made when the Air Quality setting is enabled. The full hourly array is stored so each hour in the forecast can show its AQI value.
*   **Geocoding**: Uses the native Android `Geocoder` service to translate GPS coordinates into human-readable city and country names, localized instantly to your selected app language.

## 🥽 AI Glasses Integration (Android XR)

The application features a dedicated `GlassesWeatherActivity` that projects a streamlined UI to connected AI Glasses using the **Jetpack Compose Glimmer** toolkit.

**Glimmer Implementation Details:**
*   **Target SDK:** The application targets SDK 37, a prerequisite for the latest Glimmer components.
*   **Additive Display Optimization:** The root container enforces a pure black background (`Color.Black`), which renders as 100% transparent on additive AR lenses, ensuring the UI doesn't block the real world.
*   **"One Thing at a Time":** The UI strictly adheres to Glimmer guidelines by avoiding multiple simultaneous cards. It uses `VerticalStack` to allow users to swipe between the Current Weather and a paginated Forecast, minimizing field-of-view obstruction.
*   **Legibility Constraints:** All typography enforces a minimum text size of 18sp to guarantee readability on optical displays, preventing shimmering and aliasing.
*   **Input Mapping:** Horizontal swipe gestures on the glasses' touchpad seamlessly paginate the forecast data.
### What appears on the glasses

A Glimmer-themed projected screen shows:
- **Main card**: large weather icon, current temperature (bold, 32 sp), condition subtitle, city name, and a Close button
- **Hourly forecast row**: paginated views (up to 4 pages) each showing 7 hours of data, with per-item icons and temperatures

On open the glasses speak the current weather via TTS; tapping anywhere (or the Close button) speaks "Goodbye!" and dismisses the screen.

### Technical details

| Concern | Detail |
|---|---|
| Projected activity | `GlassesWeatherActivity` — `android:requiredDisplayCategory="display_category_xr_projected"` routes it to the glasses display |
| Connection detection | `ProjectedContext.isProjectedDeviceConnected()` (API 36+); guarded with a `Build.VERSION_CODES` check so the button simply stays hidden on older SDKs |
| UI toolkit | Jetpack Compose **Glimmer** (`androidx.xr.glimmer`) — the XR-optimised design system |
| TTS | `AudioInterface`, a `DefaultLifecycleObserver` wrapping Android `TextToSpeech`; speaks on `onStart`, shuts down on `onStop`. The locale (BCP-47 tag) is passed via `EXTRA_LANGUAGE_TAG` so the TTS voice matches the phone app's language setting |
| Data transport | Intent extras (`intArrayExtra`, `doubleArrayExtra`, `getStringArrayListExtra`) — no IPC, same process |
| Deployment | APK installed on the phone only; the glasses emulator is a virtual peripheral display that pairs via Android Studio Device Manager |

### Dependencies added

```toml
# gradle/libs.versions.toml
xrProjected = "1.0.0-alpha06"
xrGlimmer   = "1.0.0-alpha11"
```

> **Note**: `compileSdk = 37` is required by Jetpack Compose Glimmer.

## 🛠 Technology Stack

Built using the latest Android development standards:

*   **Jetpack Compose**: 100% declarative UI for a fluid and reactive user experience.
*   **Material 3**: Implementation of Google's latest design tokens, including dynamic shapes and typography.
*   **Kotlin Coroutines**: Non-blocking asynchronous operations for network fetches and location updates.
*   **Retrofit & Gson**: Type-safe REST client for efficient API communication.
*   **Play Services Location**: Utilizing the Fused Location Provider for battery-efficient and accurate positioning.
*   **Compose Previews**: Extensive multi-language and multi-layout previews for rapid design iteration.

## 🧪 Testing Strategy

Quality is guaranteed through a multi-layered testing approach:

*   **Unit Tests (`test`)**: Rigorous testing of business logic, including temperature conversion, time zone formatting, and API response parsing. `GlassesWeatherTest` covers intent extra key constants (including `EXTRA_LANGUAGE_TAG`), `HourlyItem` construction from parallel arrays, edge cases (empty arrays, mismatched list lengths), temperature display, hourly time formatting (12h/24h, midnight edge cases), and BCP-47 language-tag round-trips for the TTS locale fallback logic.
*   **Compose UI Tests (`androidTest`)**: Verified interactions with buttons, switches, and maps using the `compose-ui-test` framework. Includes tests asserting the glasses button is absent when no XR device is connected (emulator default).
*   **UI Automator**: Specialized tests for handling system-level dialogs (permissions) and hardware-level changes (device orientation).
*   **Compose Preview Screenshot Tests (`screenshotTest`)**: Golden-image regression tests via the `com.android.compose.screenshot` plugin. They render the real layouts at **Phone, Foldable, and Tablet** sizes (plus the hourly/daily forecast rows and the settings dialog) and compare against committed reference PNGs. The clock is frozen to a fixed instant under preview rendering (`LocalInspectionMode`) so the goldens stay deterministic.
*   **State Persistence**: Tests ensure that your settings (language, units, location) are perfectly preserved across app restarts using `SharedPreferences`.

## 🔄 Continuous Integration

GitHub Actions (`.github/workflows/ci.yml`) runs on every push and pull request to `main`:

*   **`build` job**: lint (`lintDebug`) → unit tests (`testDebugUnitTest`) → screenshot validation (`validateDebugScreenshotTest`) → debug APK assembly (`assembleDebug`).
*   **`instrumented-tests` job**: runs `connectedDebugAndroidTest` on the emulator. Because the app targets **API 37**, instrumented coverage runs on three gating **API 37.0** legs (`google_apis_ps16k`, x86_64) across the `swiftshader`, `lavapipe`, and `auto` GPU backends. The `MainActivityAPI37Test` exercises the API 37+ code paths there.

## 🗺️ Assets & Attributions

The interactive world map utilizes a composite satellite image of Earth to provide high-precision coordinate selection.

*   **Source**: NASA's famous **"Blue Marble"** (2002 version: Land Surface, Shallow Water, and Shaded Topography).
*   **Origin**: Created by the **NASA Goddard Space Flight Center** using data from the MODIS instrument aboard the **Terra satellite**.
*   **Process**: A "cloud-free" mosaic stitched from months of observations to provide a clear view of every square kilometer of the planet.
*   **Licensing**: This image is in the **Public Domain** (created by the U.S. Federal Government). It is free to use, modify, and distribute.
*   **Verification**: This is a scientific composite based on actual satellite data, available via [NASA Visible Earth](https://visibleearth.nasa.gov/).
