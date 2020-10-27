# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2020-10-26
### Added
- Map styles as downloadable content with preview images.
- Custom car route.
- Custom public transport route
- Navigate using GPS.
- Fly to current GPS position.
- Location details shows wikipedia images.
- Fly to search result.
- Speed limit view.
- Scrollable Main Menu window and Map Styles view.
- Dialog before exiting the application with the back button.
- Public transport route calculation and description
- Log recorder and player
- Simulation and navigation for predifined and custom routes
- using PermissionsHelper.notifyOnPermissionsStatusChanged to notify permissions change.

### Changed
- Better layout for search items.
- Better layout for route description items.
- Better layout for the location details view.
- Hide toolbar at Fly to Route Instruction or Traffic.
- Providing pixelFormat and rotation to CanvasBufferRenderer.uploadFrame

### Fixed
- Flying problem at Fly to Area.
- GPS button disappears sometimes.
- Search item description wrongly formatted.
- Zoom out too much at station tap.
- Area remains highlighted after closing Fly to Area Tutorial.
- Wikipedia text appears in location details panel even if the location has no wikipedia.

### Issues
- App works slow after a location with many wikipedia images is opened.
- App works slow in simulation/ navigation mode after you pan away from current position.

## [Unreleased]
[1.5.0]: Update demo apps for SDK v7.1.20.43.693001B4
[1.4.0]: Update demo apps for SDK v7.1.20.42.AF307B1F
[1.3.0]: Update demo apps for SDK v7.1.20.41.37C8C713
[1.2.0]: Update demo apps for SDK v7.1.20.40.76465CE8
[1.1.0]: Update demo apps for SDK v7.1.20.39.5E1A0550
[1.0.0]: Initial release
