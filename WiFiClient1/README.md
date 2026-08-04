## Overview

WiFi client for the WiFiServer1 example. It discovers WiFiServer1 instances on the local network via Network Service Discovery (DNS-SD), connects to the selected one over TCP and displays the navigation data received from it: the next-turn icon (sent by the server as a ready-rendered PNG image, like in the BLEClient1 example -- no icons are bundled with this app), the distance to the next turn, the turn instruction text and the bottom panel with ETA, remaining travel time and distance.

## Requirements

- The target device must be running Android 5.0 (API level 21) or higher.
- Both devices (server and client) must be connected to the same WiFi network.

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
