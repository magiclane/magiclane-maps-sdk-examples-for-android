# Magic Lane Maps SDK Examples for Android

Explore practical examples using the Magic Lane Maps SDK for Android - including 3D maps, offline navigation, route calculation, traffic updates, and POI search. Build advanced location-based apps for mobile platforms with ease.

This repository contains dozens of open-source Android sample apps that demonstrate specific SDK features and real-world use cases.
Each example focuses on a single feature or workflow, so developers can quickly clone, explore, and integrate the sample code into their projects.

## Why use Magic Lane Maps SDK for Android

The **Magic Lane Android Maps SDK** enables developers to create feature-rich mapping and navigation apps with:
- Global coverage and offline map support
- Advanced routing for cars, bikes, trucks, and pedestrians
- Customizable 3D maps and map styles
- Voice-guided turn-by-turn navigation
- Real-time traffic updates and driver behavior analytics

## Examples

Explore the examples to learn the capabilities of the Magic Lane Maps Android SDK:

* [Android Auto Route Navigation](AndroidAutoRouteNavigation) - Start navigation from the current position to a given landmark if a route can be calculated.
* [Apply Custom Map Style](ApplyCustomMapStyle) - Present a map; apply a custom map style.
* [Apply Map Style](ApplyMapStyle) - Get the map style items from the server; download and apply a map style.
* [Avoid Geofence Area](AvoidGeofenceArea) - Calculate routes that avoid specific geographic areas.
* [Basic Shape Drawer](BasicShapeDrawer) - Show how to use `BasicShapeDrawer` class to do various drawings on a map.
* [Bike Demo](BikeDemo) - Setup a bike routing profile and start simulation from the current position to a given landmark if a route can be calculated.
* [Bike Demo Java](BikeDemoJava) - Java version of Bike Demo.
* [BLE Client](BLEClient) - BLE client for BLEServer example. It displays navigation instructions received from server.
* [BLE Client1](BLEClient1) - BLE client for BLEServer1 example. It displays navigation instructions received from server. Turn images are transferred as bitmaps.
* [BLE Client2](BLEClient2) - BLE client for BLEServer2 example. It displays navigation instructions received from server. Turn images are transferred as IDs.
* [BLE Server](BLEServer) - Start simulated navigation between 2 given landmarks. Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance. Acts like BLE server. Send navigation info (next turn image id, distance to next turn, next navigation instruction) to the BLE client.
* [BLE Server1](BLEServer1) - Start simulated navigation between 2 given landmarks. Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance. Acts like BLE server. Send navigation info (next turn image as bitmap, distance to next turn, next navigation instruction) to the BLE client.
* [BLE Server2](BLEServer2) - Start simulated navigation between 2 given landmarks. Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance. Acts like BLE server. Send navigation info (next turn image as bitmap, distance to next turn, next navigation instruction) to the BLE client.
* [Custom GPS Arrow](CustomGPSArrow) - Start simulated navigation between 2 given landmarks if a route can be calculated, using a custom GPS arrow.
* [Define Persistent Roadblock](DefinePersistentRoadblock) - Make possible the definition of a persistent roadblock.
* [Display Current Street Info](DisplayCurrentStreetInfo) - Fly to current coordinates. Display current street name, speed and road modifier.
* [Downloaded Onboard Map Simulation](DownloadedOnboardMapSimulation) - Fully offline start simulation between 2 given landmarks if any route can be calculated.
* [Downloading Onboard Map](DownloadingOnboardMap) - Gather the list of available maps; Download an onboard map.
* [Downloading Onboard Map Simulation](DownloadingOnboardMapSimulation) - Gather the list of available maps; Download an onboard map. Starts a simulation.
* [Draw Polyline](DrawPolyline) - Create a polyline and its display settings; fly to the polyline.
* [Driver Behavior](DriverBehavior) - Start recording driver behaviour analysis. View recorded analyses.
* [External Position Source Navigation](ExternalPositionSourceNavigation) - Navigate to a given landmark using an external data source (GPS positions are not coming from system. They are hardcoded in the app and they are provided to the SDK via an "ExternalDataSource" object).
* [Favourites](Favourites) - Fly to a location and use UI to add or remove that landmark to/from the favourites folder.
* [Finger Route](FingerRoute) - Calculate a route that tries to follow finger movement on the map; render route on top of finger line.
* [Fly to Area](FlyToArea) - Search for a landmark; fly to resulted landmark, if exists.
* [Fly To Coordinates](FlyToCoordinates) - Fly to given coordinates.
* [Fly to Route Instruction](FlyToRouteInstruction) - Calculate the routes between 2 given landmarks; display the first route on a map, if exists; fly to first route instruction, if exists.
* [Fly to Traffic](FlyToTraffic) - Calculate the routes between 2 given landmarks; display the first route on a map, if exists; fly to first traffic event available on route, if exists.
* [GPX Import](GPXImport) - Import a GPX file and display the Path on the map; make a route out of the GPX file.
* [GPX Route Simulation](GPXRouteSimulation) - Import a GPX file; Make a route based on the GPX and start a simulation.
* [GPX Route Simulation Java](GPXRouteSimulationJava) - Java version of GPX Route Simulation.
* [GPX Thumbnail Image](GPXThumbnailImage) - Present a thumbnail image for a route based on a GPX file without using a routing service.
* [GPX Thumbnail Image With Routing](GPXThumbnailImageWithRouting) - Calculate a route based on a GPX file and present a thumbnail image for it using a routing service.
* [Hello Fragment](HelloFragment) - Display a map on an Android Fragment.
* [Hello Fragment Custom Style](HelloFragmentCustomStyle) - Display a map on an Android Fragment; apply custom style to a map.
* [Hello Map](HelloMap) - Display a map.
* [Hello Map Compose](HelloMapCompose) - Display a map using Jetpack Compose.
* [Hello SDK](HelloSdk) - Show how the Maps SDK for Android can be integrated into your project.
* [Lane Instructions](LaneInstructions) - Calculate the routes between 2 given landmarks; display the recommended lanes for the next turn instruction.
* [Location Wikipedia](LocationWikipedia) - Search for a landmark; request Wikipedia info about first resulted landmark, if exists.
* [Map Compass](MapCompass) - Display an interactive map with a compass.
* [Map Gestures](MapGestures) - Display an interactive map supporting pan, zoom, rotate, tilt that displays logs about what gestures the user made on the map.
* [Map Perspective Change](MapPerspectiveChange) - Display an interactive map with a button that changes the view perspective between 2D and 3D.
* [Map Selection](MapSelection) - Present a map. Calculate the routes between 2 given landmarks. Illustrate how to tap on different map overlays.
* [Map Selection Compose](MapSelectionCompose) - Map selection example using Jetpack Compose.
* [Marker Collection Display Icon](MarkerCollectionDisplayIcon) - Show usage of `MarkerCollection`.
* [Monitor Geofence Area](MonitorGeofenceArea) - Get notified when crossing a geographic area.
* [Multiple Surfaces in Fragment](MultipleSurfacesInFragment) - Display a variable list of maps on an Android Fragment.
* [Multiple Surfaces in Fragment Recycler](MultipleSurfacesInFragmentRecycler) - Display a variable list of maps on an Android Fragment using RecyclerView.
* [Overlapped Maps](OverlappedMaps) - Display a second map view over the existing one.
* [Projection](Projection) - Show various types of projections for landmarks.
* [Public Transit Routing on Map](PublicTransitRoutingOnMap) - Calculate the public transport routes between 2 given landmarks; display all results on the map and fly to the main route, if exists.
* [Range Finder](RangeFinder) - Calculate the routes between 2 given landmarks; display and fly to the first route in the resulting route list, if it exists.
* [Range Finder Compose](RangeFinderCompose) - Range finder example using Jetpack Compose.
* [Route Alarms](RouteAlarms) - Start simulated navigation between 2 given landmarks if a route can be calculated.
* [Route Instructions](RouteInstructions) - Calculate the routes between 2 given landmarks; display a list with all route instructions, if exists.
* [Route Navigation](RouteNavigation) - Start navigation from the current position to a given landmark if any route can be calculated.
* [Route Restrictions](RouteRestrictions) - Start simulated navigation between 2 given landmarks using a truck (lorry) route profile with a mass exceeding 3500 kg. Listen for route restrictions during navigation. Display active route restrictions.
* [Route Simulation](RouteSimulation) - Start simulation between 2 given landmarks if any route can be calculated.
* [Route Simulation with Instructions](RouteSimulationWithInstructions) - Start simulation between 2 given landmarks. Display turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance.
* [Route Simulation with Instructions Compose](RouteSimulationWithInstructionsCompose) - Route simulation with instructions example using Jetpack Compose.
* [Route Simulation without Map](RouteSimulationWithoutMap) - Start simulated navigation between 2 given landmarks. Display turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance. No map is displayed.
* [Route Terrain Profile](RouteTerrainProfile) - Calculate the routes between 2 given landmarks; display some of the route terrain profile details available for a route.
* [Routing](Routing) - Calculate the routes between 2 given landmarks.
* [Routing on Map](RoutingOnMap) - Calculate the routes between 2 given landmarks; display and fly to the first resulted route, if exists.
* [Routing on Map Java](RoutingOnMapJava) - Calculate the routes between 2 given landmarks; display and fly to the first resulted route, if exists. Written in Java.
* [Search](Search) - Search for landmarks based on user input; display a list with landmarks found.
* [Search Along Route](SearchAlongRoute) - Start simulation between 2 landmarks. Search for gas stations along the route if the search button is pressed.
* [Search Compose](SearchCompose) - Search example using Jetpack Compose.
* [Send Debug Info](SendDebugInfo) - Show how to send the app log and latest crash report, if any, to Magic Lane support.
* [Set TTS Language](SetTTSLanguage) - Show usage of `SoundPlayingService` class and setting TTS language.
* [Social Event Voting](SocialEventVoting) - Present a social event panel that displays the score of the event and the option to vote the event.
* [Social Report](SocialReport) - Wait for a valid position. Submit a social report.
* [Speed TTS Warning Navigation](SpeedTTSWarningNavigation) - Start a navigation from the current position to a given landmark. Display the current speed and the legal speed limit. Send a TTS warning each time the current speed overcomes the legal speed limit.
* [Speed TTS Warning Tracking](SpeedTTSWarningTracking) - Display the current speed and the legal speed limit. Send a TTS warning each time the current speed overcomes the legal speed limit.
* [Speed Watcher](SpeedWatcher) - Start simulation between 2 given landmarks. Display the current speed and the legal speed limit.
* [Stay Inside Geofence Area](StayInsideGeofenceArea) - Get notified when leaving a geographic area.
* [Take Screenshot](TakeScreenshot) - Capture a screenshot of the map view by tapping the "Take Screenshot" button at the bottom of the screen. Preview the captured screenshot in a bottom sheet dialog.
* [Track Positions](TrackPostions) - Mock navigation from point A to point B using external data source while tracking positions. Display path using tracked positions.
* [Truck Profile](TruckProfile) - Calculate the truck routes between 2 given landmarks. Change the truck profile and recalculate routes depending on the new settings.
* [Voice Downloading](VoiceDownloading) - Gather the list of available voices; download first available voice.
* [Voice Instructions Route Simulation](VoiceInstrRouteSimulation) - Start simulation between 2 given landmarks if any route can be calculated; use TTS to play navigation instructions.
* [Weather](Weather) - Show how to use weather service to display forecast for a selected landmark.
* [What's Nearby](WhatsNearby) - Search all landmarks near current position; display a list of found landmarks.
* [What's Nearby Category](WhatsNearbyCategory) - Search all landmarks of a specific category near current position; display a list of found landmarks.

## Running individual examples

Individual samples can be run on an Android emulator or device.

Check the `README.md` inside each example folder for instructions.

## Building all examples

Build all examples using ``build_all.sh`` under Linux. First set environment variable `ANDROID_SDK_ROOT`, then run the script:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
./build_all.sh
```

Run `./build_all.sh --help` to see available options for building specific examples, running tests, and more.

## Configuring API Keys

An API Key is required to unlock the full functionality of these example applications. Follow our [guide](https://developer.magiclane.com/docs/guides/get-started) to generate your API Key. Check the `README.md` inside each example folder for instructions on how to set your API Key.

If no API Key is set, you can still test your apps, but a watermark will be displayed, and all the online services including mapping, searching, routing, etc. will slow down after a few minutes.

## Developer resources

- [Android SDK documentation](https://developer.magiclane.com/docs/android): Detailed guides and API references for the SDK.
- [Magic Lane Developer Portal](https://developer.magiclane.com/api/login): Manage API tokens and create custom styles.

## License

```
Copyright (C) 2021-2026 Magic Lane International B.V.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

A copy of the license is available in the repository's `LICENSE` file.

Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
