# GeneralMagic - Maps SDK for Android demo applications

Copyright (C) 2019-2022, General Magic B.V.  
All rights reserved.  
Contact: info@generalmagic.com

This repository holds a series of example projects using the **GeneralMagic - Maps SDK for Android**. More information about the API can be found on the [Documentation](https://developer.generalmagic.com/documentation) page.

This set of individual, use-case based projects is designed to be cloned by developers for their own use.

* [Android Auto Route Navigation](AndroidAutoRouteNavigation) - Start navigation from the current position to a given landmark if a route can be calculated.
* [Apply Custom Map Style](ApplyCustomMapStyle) - Present a map; apply a custom map style.
* [Apply Map Style](ApplyMapStyle) - Get the map style items from the server; download and apply a map style.
* [BLE Client](BLEClient) - BLE client for BLEServer example. It displays navigation instructions received from server.
* [BLE Server](BLEServer) - Start simulated navigation between 2 given landmarks. Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance. Acts like BLE server. Send navigation info (next turn image id, distance to next turn, next navigation instruction) to the BLE client.
* [BLE Client1](BLEClient1) - BLE client for BLEServer example. It displays navigation instructions received from server. Turn images are transferred as bitmaps.
* [BLE Server1](BLEServer1) - Start simulated navigation between 2 given landmarks. Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance. Acts like BLE server. Send navigation info (next turn image as bitmap, distance to next turn, next navigation instruction) to the BLE client.
* [Downloaded Onboard Map Simulation](DownloadedOnboardMapSimulation) - Fully offline start simulation between 2 given landmarks if any route can be calculated.
* [Downloading Onboard Map](DownloadingOnboardMap) - Gather the list of available maps; Download an onboard map.
* [Downloading Onboard Map Simulation](DownloadingOnboardMapSimulation) - Gather the list of available maps; Download an onboard map. Starts a simulation.
* [Draw Polyline](DrawPolyline) - Create a polyline and its display settings; fly to the polyline.
* [External Position Source Navigation](ExternalPositionSourceNavigation) - Navigate to a given landmark using an external data source (GPS positions are not coming from system. They are hardcoded in the app and they are provided to the SDK via an "ExternalDataSource" object)
* [Favourites](Favourites) - Fly to a location and use UI to add or remove that landmark to/ from the favourites folder.
* [Fly to Area](FlyToArea) - Search for a landmark; fly to resulted landmark, if exists.
* [Fly To Coordinates](FlyToCoordinates) - Fly to given coodinates.
* [Fly to Route Instruction](FlyToRouteInstruction) - Calculate the routes between 2 given landmarks; display the first route on a map, if exists; fly to first route instruction, if exists.
* [Fly to Traffic](FlyToTraffic) - Calculate the routes between 2 given landmarks; display the first route on a map, if exists; fly to first traffic event available on route, if exists.
* [GPX Route Simulation](GPXRouteSimulation) - Import a GPX file; Make a route based on the GPX and start a simulation.
* [Hello Fragment](HelloFragment) - Display a map on an Android Fragment.
* [Hello Fragment Custom Style](HelloFragmentCustomStyle) - Display a map on an Android Fragment; apply custom style to a map.
* [Hello Map](HelloMap) - Display a map.
* [Hello SDK](HelloSdk) - Show how the Maps SDK for Android can be integrated into your project.
* [Import GPX](ImportGPX) - Import a GPX file and display the Path on the map; make a route out of the GPX file.
* [Lane Instructions](LaneInstructions) - Calculate the routes between 2 given landmarks; display the recommended lanes for the next turn instruction.
* [Location Wikipedia](LocationWikipedia) - Search for a landmark; request Wikipedia info about first resulted landmark, if exists.
* [Map Compass](MapCompass) - Display an interactive map with a compass.
* [Map Gestures](MapGestures) - Display an interactive map supporting pan, zoom, rotate, tilt that displays logs about what gestures the user made on the map.
* [Map Perspective Change](MapPerspectiveChange) - Display an interactive map with a button that changes the view perspective between 2D and 3D.
* [Multiple surfaces in Fragment](MultipleSurfacesInFragment) - Display a variable list of maps on an Android Fragment.
* [Multiple surfaces in Fragment Recycler](MultipleSurfacesInFragmentRecycler) - Displaying a variable list of maps on an Android Fragment.
* [Overlapped Maps](OverlappedMaps) - Display a second map view over the existing one.
* [Public Transit Routing on Map](PublicTransitRoutingOnMap) - Calculate the public transport routes between 2 given landmarks; display all results on the map and fly to the main route, if exists.
* [Range Finder](RangeFinder) - Calculate the routes between 2 given landmarks; display and fly to the first route in the resulting route list, if it exists.
* [Route Alarms](RouteAlarms) - Start simulated navigation between 2 given landmarks if a route can be calculated.
* [Route Instructions](RouteInstructions) - Calculate the routes between 2 given landmarks; display a list with all route instructions, if exists.
* [Route Navigation](RouteNavigation) - Start navigation from the current position to a given landmark if any route can be calculated.
* [Route Simulation](RouteSimulation) - Start simulation between 2 given landmarks if any route can be calculated.
* [Route Simulation with Instructions](RouteSimulationWithInstructions) - Start simulation between 2 given landmarks. Displays turn-by-turn navigation instructions, estimated time of arrival, remaining time and distance.
* [Route Terrain Profile](RouteTerrainProfile) - Calculate the routes between 2 given landmarks; display some of the route terrain profile details available for a route.
* [Routing](Routing) - Calculate the routes between 2 given landmarks.
* [Routing on Map](RoutingOnMap) - Calculate the routes between 2 given landmarks; display and fly to the first resulted route, if exists.
* [Routing on Map Java](RoutingOnMapJava) - Calculate the routes between 2 given landmarks; display and fly to the first resulted route, if exists.
* [Search](Search) - Searches for landmarks based on user input; display a list with landmarks found.
* [Search Along Route](SearchAlongRoute) - Start simulation between 2 landmarks. Search for gas stations along the route if the search button is pressed.
* [Send Debug Info](SendDebugInfo) - This example app shows how to send the app log and latest crash report, if any, to General Magic support.
* [Set TTS Language](SetTTSLanguage) - Shows usage of `SoundPlayingService` class and setting TTS language.
* [Social Report](SocialReport) - Waits for a valid position. Submit a social report.
* [Speed Watcher](SpeedWatcher) - Start simulation between 2 given landmarks. Display the current speed and the legal speed limit.
* [Speed TTS Warning](SpeedTTSWarning) - Starts a navigation from the current position to a given landmark. Displays the current speed and the legal speed limit. Sends a TTS warning each time the current speed overcomes the legal speed limit.
* [Voice Downloading](VoiceDownloading) - Gather the list of available voices; download first available voice.
* [Voice Instructions Route Simulation](VoiceInstrRouteSimulation) - Start simulation between 2 given landmarks if any route can be calculated; use TTS to play navigation instructions.
* [What's Nearby](WhatsNearby) - Searches all landmarks near current position; display a list of found landmarks.
* [What's Nearby Category](WhatsNearbyCategory) - Searches all landmarks of a specific category near current position; display a list of found landmarks.

**Note:** As the very first step, we highly recommend that you get a token from [General Magic Portal](https://developer.generalmagic.com/api). If no token is set, you can still test your apps, but a watermark will be displayed, and all the online services including mapping, searching, routing, etc. will slow down after a few minutes.

**Note:** Build all examples by using `build_all.sh`. First set environment variable `ANDROID_SDK_ROOT`, e.g.: `export ANDROID_SDK_ROOT=/path/to/android-sdk` and then call `build_all.sh <path/to/sdk/.aar>`

## License

Unless otherwise noted in `LICENSE` files for specific files or directories, the [LICENSE](LICENSE) in the root applies to all content in this repository.
