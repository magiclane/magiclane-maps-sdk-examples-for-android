## Overview

This example app demonstrates the following features:
- Start navigation from the current position to a given landmark if a route can be calculated.

## Build instructions

Step 1. Download the SDK

Step 2. Extract SDK to the predefined folder (app/libs)

Step 3. Load the project into ```Android Studio```.

Step 4. ```File``` -> ```Sync project with gradle files```.

Step 5. Select desired ```Active Build Variant``` (debug/release) in ```Build Variants``` menu.

Step 6. Deploy to device as usual.


## How to run DHU
-1. (required)
Install libs:
sudo apt install libssl1.0.0
      - Edit the source list `sudo nano /etc/apt/sources.list` to add the following line: `deb http://security.ubuntu.com/ubuntu xenial-security main`
      - Then `sudo apt update` and `sudo apt install libssl1.0.0`.
sudo apt-get install libportaudio-dev
sudo apt-get install SDL2-ttf-2.0
sudo apt-get install libsdl2-2.0
sudo apt-get install libsdl2-dev

(optional)
0. Create "cluster.ini" file where desktop-head-unit executable is located.
   See: https://developer.android.com/training/cars/apps/navigation#trip-information

(required)
1. Enable developer mode in Android and in Android auto app.
   In AndroidAuto -> Settings -> 10x click on "Version"
2. Start android auto head unit server from Android Auto phone app.
3. Connect phone to USB.   
   Each time you plug/unplug the phone:
    1. adb kill-server
    2. adb forward tcp:5277 tcp:5277
    3. ./desktop-head-unit

If everything is ok android auto app will go in background and DHU will display the image.
   
Note. desktop-head-unit is found under : Android/Sdk/extras/google/auto
