# Check all the great features of Maps SDK for Android with Kotlin API

## Overview

TutorialAppKotlin app presents how you can perform a search, download a map, change the map style, make a route from point A to point B and more.

Map:
- Change map style (how the map is displayed, e.g. satellite imagery, terrain topography, day, night, plain, combinations)
- One map view (a single map on the entire screen)
- Tiled map views (2 maps, each one covering half of the screen)
- Overlapped map views (2 maps, one covering the whole screen and a smaller inset map overlapping the other map)

Search:
- Free text search (search for any location)
- What's nearby (see points of interest such as stores or nature reserves etc, near a specific location)
- Gas stations (show the locations of a specific category of points of interest near a given location, such as gas stations, restaurants, stores etc)

Routing:
- A to B route (a route from A to B is drawn on the map and you can simulate the route or see what are the route instructions that you have to follow to get from A to B)
- A to B via C route (same as the above one, but you have a route from A to B via an additional waypoint, C)
- During a route simulation, various statistics are available: route instructions, road information, current speed and the speed limit in that location, traffic information, lane information, arrival time, remaining time, remaining distance.

Maps:
- Download maps (if you are online, you can download maps for each country, even country regions for certain big countries)

Fly to:
- Fly to landmark (shows a certain location on the map and details about the specific location. If the location has a Wikipedia page, Wikipedia images for it are displayed, along with the description of the location and the Wikipedia page can be accessed as well)
- Fly to area (shows a certain area on the map and details about it. If the area has a Wikipedia page, Wikipedia images for it are displayed, along with the description of the area, and the Wikipedia page can be accessed as well)
- Fly to route (shows a route on the map and navigation can be simulated on that route, or the route instructions can be displayed)
- Fly to route instructions (shows a route on the map and the next turn icon/image along with text instructions in a panel at the top of the screen is displayed, e.g. "Leave the roundabout at the 1st exit", including the distance to the next move)
- Fly to traffic (shows an area on the map that has heavy traffic and a panel at the top of the screen indicates that there is queuing traffic for a certain distance/period of time e.g. "1 min, 1.3 km, Queuing traffic")

Server:
- Set custom server (it is possible to select and use a custom server)

## Build instructions

Step 1. Download the SDK

Step 2. Extract SDK to the predefined folder (app/libs)

Step 3. Load the project into ```Android Studio```.

Step 4. ```File``` -> ```Sync project with gradle files```.

Step 5. Select desired ```Active Build Variant``` (debug/release) in ```Build Variants``` menu.

Step 6. Deploy to device as usual.
