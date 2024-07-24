## Overview

This example app demonstrates the following features:
- Present a map.
- Start simulated navigation between 2 given landmarks.
- Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance.
- Acts like BLE server. Send navigation info (next turn image as bitmap, distance to next turn, next navigation instruction) to the BLE client.

## Requirements

- Deployment target device must run Android 6.0 (API level 23) or higher.
- Deployment target device must be connected to internet.
- Bluetooth setting should be ON.

## Build instructions

Step 1. Download the SDK

Step 2. Extract SDK to the predefined folder (app/libs)

Step 3. Load the project into ```Android Studio```.

Step 4. ```File``` -> ```Sync Project with Gradle Files```.

Step 5. Select desired ```Active Build Variant``` (debug/release) in ```Build Variants``` menu.

Step 6. Deploy to device as usual.
