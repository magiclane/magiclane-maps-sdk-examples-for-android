# GeneralMagic - Maps SDK for Android demo applications

Copyright (C) 2019-2021, General Magic B.V.  
All rights reserved.  
Contact: info@generalmagic.com

This repository holds a series of example projects using the **GeneralMagic - Maps SDK for Android**. More information about the API can be found on the [Documentation](https://developer.generalmagic.com/documentation) page.

This set of individual, use-case based projects is designed to be cloned by developers for their own use.

* [Hello SDK](HelloSdk) - Show how the Maps SDK for Android can be integrated into your project
* [Hello Map](HelloMap) - Display a map
* [Hello Fragment](HelloFragment) - Display a map on an Android Fragment
* [Hello Fragment Custom Style](HelloFragmentCustomStyle) - Display a map on an Android Fragment; apply custom style to a map
* [Multiple surfaces in Fragment](MultipleSurfacesInFragment) - Display a variable list of maps on an Android Fragment
* [Multiple surfaces in Fragment Recycler](MultipleSurfacesInFragmentRecycler) - Displaying a variable list of maps on an Android Fragment
* [Online Maps](OnlineMaps) - Gather the list of available maps; download first available map
* [Overlapped Maps](OverlappedMaps) - Display a second map view over the existing one
* [Map Style](ApplyMapStyle) - Gather the list of available map styles; download and apply one
* [Apply Custom Map Style](ApplyCustomMapStyle) - Present a map; apply a custom map style
* [Apply Map Style](ApplyMapStyle) - Get the map style items from the server; download and apply a map style
* [Draw Polyline](DrawPolyline) - Create a polyline and its display settings; fly to the polyline
* [Import GPX](ImportGPX) - Import a GPX file and display the Path on the map; make a route out of the GPX file
* [Routing](Routing) - Calculate the routes between 2 given landmarks
* [Route Instructions](RouteInstructions) - Calculate the routes between 2 given landmarks; display a list with all route instructions, if exists
* [Route Navigation](RouteNavigation) - Start navigation from the current position to a given landmark if any route can be calculated
* [Route Simulation](RouteSimulation) - Start simulation between 2 given landmarks if any route can be calculated
* [Route Simulation with Sound](RouteSimulationWithSound) - Start simulation between 2 given landmarks if any route can be calculated; use TTS to play navigation instructions
* [Routing on Map](RoutingOnMap) - Calculate the routes between 2 given landmarks; display and fly to the first resulted route, if exists
* [Route Terrain Profile](RouteTerrainProfile) - Calculate the routes between 2 given landmarks; display some of the route terrain profile details available for a route
* [Search](Search) - Searches for landmarks based on user input; display a list with landmarks found
* [Fly to Area](FlyToArea) - Search for a landmark; fly to resulted landmark, if exists
* [Fly To Coordinates](FlyToCoordinates) - Fly to given coodinates
* [Fly to Route Instruction](FlyToRouteInstruction) - Calculate the routes between 2 given landmarks; display the first route on a map, if exists; fly to first route instruction, if exists
* [Fly to Traffic](FlyToTraffic) - Calculate the routes between 2 given landmarks; display the first route on a map, if exists; fly to first traffic event available on route, if exists
* [Location Wikipedia](LocationWikipedia) - Search for a landmark; request Wikipedia info about first resulted landmark, if exists
* [Public Transit Routing on Map](PublicTransitRoutingOnMap) - Calculate the public transport routes between 2 given landmarks; display all results on the map and fly to the main route, if exists
* [What's Nearby](WhatsNearby) - Searches all landmarks near current position; display a list of found landmarks
* [What's Nearby Category](WhatsNearbyCategory) - Searches all landmarks of a specific category near current position; display a list of found landmarks
* [Tutorial Demo App](TutorialAppKotlin) - Presents how you can perform a search, download a map, change the map style, make a route from point A to point B and more

**Note:** As the very first step, we highly recommend that you get a token from [General Magic Portal](https://developer.generalmagic.com/api). If no token is set, you can still test your apps, but a watermark will be displayed, and all the online services including mapping, searching, routing, etc. will slow down after a few minutes.
**Note:** Build all examples by using `build_all.sh`. First set environment variable `ANDROID_SDK_ROOT`, e.g.: `export ANDROID_SDK_ROOT=/path/to/android-sdk` and then call `build_all.sh <path/to/sdk/.aar>`

## License

Unless otherwise noted in `LICENSE` files for specific files or directories, the [LICENSE](LICENSE) in the root applies to all content in this repository.
