## Overview

This example app demonstrates the following features:
- Define a persistent roadblock like Magic Earth does: tap a road to start, pan the map to trace the road-following path preview under the target cross, commit segment points with the "+" button and finish with the "✓" button (the `RoadblockDefinitionToolbar` composable from the maps-compose library).
- Choose the roadblock name and validity interval in a set-roadblock panel, then define the roadblock with `Traffic.addPersistentRoadblock`.
- List the active persistent roadblocks and present any of them on the map (the `RoadblockCard` composable from the maps-compose library), with the roadblock centered in the map area left free by the info panel.
- Select the transport mode the roadblock applies to and delete roadblocks from the list or from the map.
- Use Jetpack Compose.

## Requirements

- The target device must be running Android 5.0 (API level 21) or higher.
- An active internet connection is required on the target device.

## Set API Key

To unlock the full functionality of this example app, follow our [step-by-step guide](https://developer.magiclane.com/docs/guides/get-started) to sign up for a free account, create a project and generate an API key.

Define `GEM_TOKEN` as an environment variable or in `gradle.properties` before building the project, so the build system can access your API key. For example, add the following line to your `gradle.properties` file:

```properties
GEM_TOKEN=your_api_key_here
```

> **Note:**  
> You may still test your applications without an API key; however, a watermark will be displayed, and access to online services - including mapping, search, and routing - will be significantly slowed after a few minutes.

## Build Instructions

1. Open the project in **Android Studio**.
2. Navigate to **File** > **Sync Project with Gradle Files**.
3. Deploy the application to your device as you normally would.

## Getting Help

- **Bug Reports:**  
  If you encounter a bug, please [open an issue](https://github.com/magiclane/magiclane-maps-sdk-examples-for-android/issues). If possible, include the version of Magic Lane Maps SDK for Android and a minimal example that reproduces the problem.

- **Example Requests:**  
  If you would like to request a new example, please [open an issue](https://github.com/magiclane/magiclane-maps-sdk-examples-for-android/issues). Describe what the example should achieve and the motivation behind your request.
