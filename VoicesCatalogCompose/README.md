## Overview

This example app demonstrates the following features:
- Browse the online human-voices catalog, organized by continent, with voices grouped by language and country — a Compose replica of the Magic Earth voices page.
- Download voices, pause / resume the downloads (individually or per language group with Download All / Pause All / Resume All), and follow the progress on circular per-item indicators.
- List the voices that are on the device on a dedicated Offline tab, with a summary card (voice count, total size and the applied voice), a selectable Text-to-Speech row (with a shortcut to the device TTS settings) and expandable language groups.
- Apply a downloaded voice as the navigation guidance voice by tapping its row; the applied voice is marked with a check (and falls back to Text-to-Speech when deleted).
- Delete a single voice (swipe a row on the Online tab, or use the delete buttons on the Offline tab), all the voices of a language, or all downloaded voices at once — all guarded by confirmation dialogs.
- Search the catalog by speaker name, language or country (diacritics-insensitive), with flat voice-row results on both tabs.
- Use the voices-catalog composables from the maps-compose library (`OnlineVoicesTab`, `OfflineVoicesTab`, `VoiceRow`, `VoiceGroupHeaderRow`, `VoiceGenderBadge`, `TtsRow`, `VoicesSummaryCard`, `SegmentedTabControl`, `CatalogSearchField`, …).
- Use Jetpack Compose.

## Requirements

- The target device must be running Android 5.0 (API level 21) or higher.
- An active internet connection is required on the target device to browse and download voices.

## Set API Key

To unlock the full functionality of this example app, follow our [step-by-step guide](https://developer.magiclane.com/docs/guides/get-started) to sign up for a free account, create a project and generate an API key.

Define `GEM_TOKEN` as an environment variable or in `gradle.properties` before building the project, so the build system can access your API key. For example, add the following line to your `gradle.properties` file:

```properties
GEM_TOKEN=your_api_key_here
```

> **Note:**  
> You may still test your applications without an API key; however, a watermark will be displayed, and access to online services - including mapping, search, and routing - will be significantly slowed after a few minutes.

## Build Instructions

1. Open the project in **Android Studio**.[JNISounds.cpp](../../../SDK/Android/GEMFramework/SDK/src/main/cpp/Src/SDK/Core/JNISounds.cpp)
2. Navigate to **File** > **Sync Project with Gradle Files**.
3. Deploy the application to your device as you normally would.

## Getting Help

- **Bug Reports:**  
  If you encounter a bug, please [open an issue](https://github.com/magiclane/magiclane-maps-sdk-examples-for-android/issues). If possible, include the version of Magic Lane Maps SDK for Android and a minimal example that reproduces the problem.

- **Example Requests:**  
  If you would like to request a new example, please [open an issue](https://github.com/magiclane/magiclane-maps-sdk-examples-for-android/issues). Describe what the example should achieve and the motivation behind your request.
