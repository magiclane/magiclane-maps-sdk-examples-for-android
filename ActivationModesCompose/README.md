## Overview

This example app demonstrates the following features:
- Every way an SDK with auto-activation (the SDKs downloaded from the Magic Lane website) can become, and stop being, activated.
- How an application can reflect the SDK's activation state to its user, here with a watermark text on the map (`MapView.setWatermarkText`).
- The manual offline activation and deactivation ceremony for devices that never go online, with the request blob shown as a QR code for the Magic Lane companion app and as the REST request a developer can send themselves.
- Use Jetpack Compose.

## Requirements

- The target device must be running Android 5.0 (API level 21) or higher.
- An internet connection is only needed for the auto-activation scenario; the offline scenarios work without one.

## Set API Key

To unlock the full functionality of this example app, follow our [step-by-step guide](https://developer.magiclane.com/docs/guides/get-started) to sign up for a free account, create a project and generate an API key.

Define `GEM_TOKEN` as an environment variable or in `gradle.properties` before building the project, so the build system can access your API key. For example, add the following line to your `gradle.properties` file:

```properties
GEM_TOKEN=your_api_key_here
```

> **Note:**
> This example needs an API key: activation is about binding the SDK on a device to your project, so without a key there is nothing to activate.

## How to use the sample

The example initializes the SDK itself, with the internet connection **disallowed**, like a device without connectivity. Since the SDK cannot auto-activate, it reports itself as not activated (`GemSdk.onSdkNotActivated`) and the map shows the watermark text `SDK not activated / Limited offline functionality`. The panel shows the activation state, the connection state, the application id, and the last notification received from the SDK, then offers four sections:

1. **Auto-activation (online)** - switch on *Allow internet connection*. The SDK reaches Magic Lane Services, activates itself from the project token, reports `GemSdk.onSdkActivated`, and the watermark disappears. Switch it off to go back to an offline device.
2. **Manual offline activation** - for a device that must never go online. Optionally enter a portal-issued license key (leave it empty to let the SDK generate one), then press *Get activation request blob*. The blob is produced entirely on-device and shown three ways: as a QR code to scan with the Magic Lane companion app, as raw text (long-press to copy), and as the exact REST request you can send from any online machine. While the activation service URL is unknown the panel explains how to look it up from the services list (`GET https://m71os.services.magicearthsdk.com/services_list_json`, entry with `"service_id": 3`). Both routes return an `offline_activation_key`; type it in and press *Complete offline activation*. The SDK becomes activated and the watermark disappears.
3. **Manual offline deactivation** - the mirror of the above, to release this device's activation (e.g. to decommission it or reuse its license key). Press *Get deactivation request blob* - the activation becomes pending deactivation at this point, so the SDK reports not activated and the watermark returns right away - take the blob to Magic Lane Services the same way, type the returned `offline_deactivation_key` and press *Complete offline deactivation* to tidy up the record on this device.
4. **Reset to first run** - a testing aid: disallows the connection and deletes this device's local activation records, so the SDK reports not activated again and every scenario above can be replayed without restarting the app. Only the local records are dropped - nothing is released at Magic Lane Services, so a real device must use the offline deactivation above.

## How it works

1. The activation-state notifications are assigned to `GemSdk.onSdkNotActivated` / `GemSdk.onSdkActivated` **before** the SDK is initialized, because with a token but no connection the SDK already reports "not activated" during initialization. They are delivered on the main thread.
2. The SDK is initialized by the example with `GemSdk.initSdkWithDefaults(context, allowOnline = false)`, so the connection starts disallowed; `GemMap` then hosts the already initialized SDK. The panel toggles the connection with `SdkSettings.allowConnection`. Allowing it lets auto-activation run; the SDK then reports `onSdkActivated`.
3. The watermark is the application's own reaction to the notifications - the SDK draws nothing for this state. While not activated the example calls `mapView.setWatermarkText("SDK not activated", "Limited offline functionality")`, and clears it once activated.
4. The application id (the audience of the token) required by the offline calls is obtained with `SdkSettings.applicationId`, so the application does not need to keep the token around.
5. Manual offline activation uses `ActivationService().getOfflineActivationRequestBlob()` to produce the request blob on-device (no network involved), and `completeOfflineActivation()` with the key returned by Magic Lane Services. Deactivation uses `getOfflineDeactivationRequestBlob()` with the license key of the active activation (read from `getActivationsForProduct()`) and `completeOfflineDeactivation()`.
6. The request blob is rendered as a QR code with ZXing for the companion app, and the equivalent REST request is displayed for developers who prefer to call the service themselves: `POST` (activation) or `DELETE` (deactivation) to `{activation service url}/services/tokens/v1/activations` with the JSON body `{"request_blob": "..."}`.
7. The reset button disallows the connection and calls `ActivationService().deleteActivation()` for every record returned by `getActivationsForProduct()`; the SDK reports `onSdkNotActivated` immediately, and the next time the connection is allowed auto-activation runs again.

## Build Instructions

1. Open the project in **Android Studio**.
2. Navigate to **File** > **Sync Project with Gradle Files**.
3. Deploy the application to your device as you normally would.

## Getting Help

- **Bug Reports:**
  If you encounter a bug, please [open an issue](https://github.com/magiclane/magiclane-maps-sdk-examples-for-android/issues). If possible, include the version of Magic Lane Maps SDK for Android and a minimal example that reproduces the problem.

- **Example Requests:**
  If you would like to request a new example, please [open an issue](https://github.com/magiclane/magiclane-maps-sdk-examples-for-android/issues). Describe what the example should achieve and the motivation behind your request.
