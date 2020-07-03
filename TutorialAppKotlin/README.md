# Check all the great features of Maps SDK for Android with Kotlin API

## Overview

TutorialAppKotlin app presents how you can perform a search, download a map, change the map style, make a route from point A to point B and more.

Map:
- Change map style (option to change how you see the map, e.g. satelitte, terrain)
- One map view (you see one big map on your screen)
- Two tiled map views (you see 2 maps, each one covering half of your screen)
- Two overlapped map views (you see 2 maps, one covering the whole screen and a smaller one that is overlapping the big one)

Search:
- Free text search (you can search for any location)
- What's nearby (you can see what's nearby a specific location)
- Gas stations (you can see gas stations nearby a specific location, meaning you can see any other location category in the nearby area e.g. restaurants)

Routing:
- A to B route (a route from A to B is drew on the map and you can simulate the route or see what are the route instructions that you have to follow to reach your destination)
- A to B via C route (same as the above one, but you have a route from A to B via C)
- While you are in a route simulation, you have plenty of information: route instructions, road information, current speed and the speed limit in that location, traffic information, lane information, arrival time, remaining time, remaining distance.

Maps:
- Download maps (if you are online, you can download maps for each country, even some country regions for really big countries)

Fly to:
- Fly to landmark (shows a certain location on the map and details about the specific location. If the location has a Wikipedia page, you can see Wikipedia images for it, read the description of the location and access the Wikipedia page)
- Fly to area (shows a certain area on the map and details about it. If the area has a Wikipedia page, you can see Wikipedia images for it, read the description of the area and access the Wikipedia page)
- Fly to route (shows a route on the map and you can simulate it or see the route instructions)
- Fly to route instructions (shows a route on the map and you can see the next move you should make as an image and as text instruction in a black panel on the top of your screen e.g. "Leave the roundabout at the 1st exit", including the distance to the next move)
- Fly to traffic (shows on the map an area that has heavy traffic and you are informed in a black panel on the top of your screen that you are queuing traffic for a distance/ period of time e.g. "1 min, 1.3 km, Queuing traffic")

Server:
- Set custom server (you can choose to use a custom server)

## Build instructions

Step 1. Download the SDK

Step 2. Extract SDK to the predefined folder (app/libs)

Step 3. Load the project into ```Android Studio```.

Step 4. ```File``` -> ```Sync project with gradle files```.

Step 5. Select desired ```Active Build Variant``` (debug/release) in ```Build Variants``` menu.

Step 6. Deploy to device as usual.
