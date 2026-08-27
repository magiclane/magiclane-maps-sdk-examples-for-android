## Overview

This example app demonstrates the following features:
- Present a map.
- Define a complex (multi-segment) persistent roadblock, using the Magic Earth mechanism: tap a road to start, pan the map and press the "+" button to add a new segment underneath the center target, then press the "✓" button (or cancel via the bottom left button / Back, with confirmation).
- Set the roadblock name and validity interval in the panel opened by the "✓" button, with date and time pickers for both interval ends, then commit via "Done".
- Select the transport mode the roadblock applies to (Car, Truck or Bike) via the bottom right settings button.
- View the active persistent roadblocks in a list (top right button) and delete one by tapping its red garbage button.
- Tap a roadblock icon on the map to open its info panel, showing the same details as the list, from where the roadblock can also be deleted.

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
