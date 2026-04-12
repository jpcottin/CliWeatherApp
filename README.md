# CliWeatherApp

CliWeatherApp is a robust, modern Android weather application built with Jetpack Compose. It provides real-time weather updates, detailed forecasts, and interactive location tools, all wrapped in a beautiful, responsive Material 3 interface.

## 🌟 Key Features

*   **Comprehensive Weather Data**: Instant access to current temperature, weather conditions, and icons.
*   **Detailed Forecasts**:
    *   **6-Hour Forecast**: Hourly temperature and condition breakdown.
    *   **6-Day Forecast**: Daily high/low ranges for the upcoming week.
*   **Dual-Mode Location Support**:
    *   **Interactive World Map**: Tap anywhere on the high-precision map to fetch weather for that specific coordinate.
    *   **Real-time GPS**: Automatic tracking of your device's movement. Fully compatible with `adb emu geo fix` for emulator testing.
*   **Text-to-Speech (TTS)**: Listen to your weather report in your preferred language.
*   **Social Sharing**: Capture a snapshot of your current weather and share it instantly with friends.
*   **Multi-Device Optimized**: Native support for Phone, Tablet, and Foldable layouts using `WindowSizeClass`.

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
*   **Location Mode**: Switch between **Map Mode** (fixed point) and **GPS Mode** (continuous tracking).

## 🔒 Permissions

The app requests minimal permissions to ensure user privacy while providing full functionality:

*   `INTERNET`: Required to fetch weather data from the Open-Meteo API and perform reverse geocoding.
*   `ACCESS_FINE_LOCATION`: Required for high-accuracy GPS tracking.
*   `ACCESS_COARSE_LOCATION`: Required for approximate location tracking and compatibility with Android 12+ "Approximate Location" privacy settings.

## 🌐 External API

CliWeatherApp utilizes the **[Open-Meteo API](https://open-meteo.com/)** for all weather data.
*   **Transparency**: No API keys are required for standard usage.
*   **Data Points**: The app fetches `hourly=temperature_2m,weathercode,is_day` and `daily=weathercode,temperature_2m_max,temperature_2m_min`.
*   **Geocoding**: Uses the native Android `Geocoder` service to translate GPS coordinates into human-readable city and country names.

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

*   **Unit Tests (`test`)**: Rigorous testing of business logic, including temperature conversion, time zone formatting, and API response parsing.
*   **Compose UI Tests (`androidTest`)**: Verified interactions with buttons, switches, and maps using the `compose-ui-test` framework.
*   **UI Automator**: Specialized tests for handling system-level dialogs (permissions) and hardware-level changes (device orientation).
*   **State Persistence**: Tests ensure that your settings (language, units, location) are perfectly preserved across app restarts using `SharedPreferences`.

## 🗺️ Assets & Attributions

The interactive world map utilizes a composite satellite image of Earth to provide high-precision coordinate selection.

*   **Source**: NASA's famous **"Blue Marble"** (2002 version: Land Surface, Shallow Water, and Shaded Topography).
*   **Origin**: Created by the **NASA Goddard Space Flight Center** using data from the MODIS instrument aboard the **Terra satellite**.
*   **Process**: A "cloud-free" mosaic stitched from months of observations to provide a clear view of every square kilometer of the planet.
*   **Licensing**: This image is in the **Public Domain** (created by the U.S. Federal Government). It is free to use, modify, and distribute.
*   **Verification**: This is a scientific composite based on actual satellite data, available via [NASA Visible Earth](https://visibleearth.nasa.gov/).

---
*Created by Gemini CLI - 2026*
