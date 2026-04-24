# CliWeatherApp

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

When an Android XR projected display (AI glasses) is paired and connected to the phone, a glasses icon button appears **to the right of the Share button**. Tapping it sends the current weather to the glasses.

### What appears on the glasses

A Glimmer-themed projected screen shows:
- **Main card**: large weather icon, current temperature (bold, 32 sp), condition subtitle, city name, and a Close button
- **Hourly forecast row**: the next 4 hours, each showing the hour label, a weather icon, and temperature

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
xrProjected = "1.0.0-alpha03"
xrGlimmer   = "1.0.0-alpha02"
```

> **Note**: `compileSdk = 36` is required by `androidx.xr.projected`.

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
*   **State Persistence**: Tests ensure that your settings (language, units, location) are perfectly preserved across app restarts using `SharedPreferences`.

## 🗺️ Assets & Attributions

The interactive world map utilizes a composite satellite image of Earth to provide high-precision coordinate selection.

*   **Source**: NASA's famous **"Blue Marble"** (2002 version: Land Surface, Shallow Water, and Shaded Topography).
*   **Origin**: Created by the **NASA Goddard Space Flight Center** using data from the MODIS instrument aboard the **Terra satellite**.
*   **Process**: A "cloud-free" mosaic stitched from months of observations to provide a clear view of every square kilometer of the planet.
*   **Licensing**: This image is in the **Public Domain** (created by the U.S. Federal Government). It is free to use, modify, and distribute.
*   **Verification**: This is a scientific composite based on actual satellite data, available via [NASA Visible Earth](https://visibleearth.nasa.gov/).
