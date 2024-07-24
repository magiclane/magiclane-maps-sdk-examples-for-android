## Overview

This example app demonstrates the following features:
- Get the list of available maps from the server.
- Download an onboard map.
- Start a simulation.

## Requirements

- Deployment target device must run Android 5.0 (API level 21) or higher.
- Deployment target device must be connected to internet for downloading the map.

## Run instructions

1. Run once connected to internet and wait for the onboard map download to finish.
2. Now you can disconnect from internet.
3. Run again with onboard map downloaded. Simulation must start.

## Build instructions

Step 1. Download the SDK

Step 2. Extract SDK to the predefined folder (app/libs)

Step 3. Load the project into ```Android Studio```.

Step 4. ```File``` -> ```Sync Project with Gradle Files```.

Step 5. Select desired ```Active Build Variant``` (debug/release) in ```Build Variants``` menu.

Step 6. Deploy to device as usual.
