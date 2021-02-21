# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.3.0] - 2021-02-19
### Added
- History view.

### Issues
- Tap on Public Transport route item from History does not open a PT route on map.
- No Route Profile for routes after tapping an item from History.

## [3.2.0] - 2021-02-15
### Added
- Address Search option.
- Route Profile View for every route.
- Fly To Line option (fly to a drawn line on the map).

### Fixed
- "Play" button does not start navigation on custom routes.

## [3.1.0] - 2021-01-18
### Added
- SeekBar to LogPlayerController.

## [3.0.0] - 2020-12-23
### Added
- UploadLog form activity.
- LogUploader instance kept in GemApplication.

### Fixed
- not declared MODIFY_AUDIO_SETTINGS permission.

## [2.5.0] - 2020-12-16
### Added
- Audio setting and used it in log recorder tutorial.

### Fixed
- Pick log activity list sort.
- App working slow while in a simulation/navigation after you pan away from current position.

### Issues
- Camera might freeze after spamming log recorder start stop.
- App works slow after a location with many wikipedia images is opened.

## [2.4.0] - 2020-12-09
### Added
- Settings : Keep recent minutes and disk limit for recorder.
- LogRecorderController uses new added settings.
- Pick Log Activity: protect and export log options when long pressing a list item.
- Pick Log Activity: accepts multiple directories as input.
- Traffic, toll and ferry icons on route overview bubble.
- Download a second style at the first app run and apply it for multi-views.

### Changed
- Recorder directories are no longer created by user, they are done internally if not exists.
- Logs are not auto exported to public storage! You must export them manually.
- Canvas buffer renderer frame fit setting from eCenter to eFitInside.
- Pick location design.

### Fixed
- Simulation starting
- Deleting a style after downloading it no longer deletes the preview image as well.
- Typing fast in search gets bad results.
- Permissions not granted when you first run the app.

### Issues
- Camera might freeze after spamming log recorder start stop.
- App works slow after a location with many wikipedia images is opened.
- App works slow in simulation/ navigation mode after you pan away from current position.

## [2.3.0] - 2020-11-23
### Added
- Settings Tutorial.
- Button decorator helper which prepares buttons as needed across the map activity tutorials.
- Management of tutorials. See Tutorials.kt.

### Changed
- Renamed BaseLayoutController to MapLayoutController.
- Bottom buttons used across tutorials over the map are now accesible only by BaseLayoutController.
- All System Activity actions are final and will notice GEMApplication.
- Tutorials can be opened in a static way. Ex: Tutorials.openHelloWorldTutorial().
- Moved SDK init things from MainActivity to GEMApplication. See GEMApplication.init.
- Changed way of handling thread calls.

### Removed
- StaticsHoler
- MapFollowingProvider

### Issues
- Camera might freeze after spamming log recorder start stop.
- App works slow after a location with many wikipedia images is opened.
- App works slow in simulation/ navigation mode after you pan away from current position.
- Deleting a style after downloading it deletes the preview image as well.

## [2.2.0] - 2020-11-12
### Added
- Tap on route description items displayes them on the map.
- Close button on location details view.

### Changed
- BaseActivity and BaseLayoutController: disableScreenLock and enableScreenLock merged into setScreenAlwaysOn.
- BaseLayoutController added setRequestedOrientation.
- Updated Online Maps design.
- Display marker or area contour on map after going to a search result.
- Filtering at searching in the Online Maps list.
- Replaced deprecated methods used.

### Fixed
- Log Recording not displaying images.
- Log Player not freezing when pressing X button.
- Wikipedia images stop loading if one image failes to load.

### Issues
- Camera might freeze after spamming log recorder start stop.
- App works slow after a location with many wikipedia images is opened.
- App works slow in simulation/ navigation mode after you pan away from current position.
- Deleting a style after downloading it deletes the preview image as well.

## [2.1.0] - 2020-11-1
### Changed
- Log Player uses pause/resume functionality when using play/stop button.
- Recorder's listeners not requesting critical data anymore.
- Fixed visibility of start stop button of log player.
- Public Transport Route Description now displays full information.
- Public Transport route segments now can be expanded and tapped for map display.
- Map Styles has a new design.

### Fixed
- Fixed not hiding canvas when ending log recorder/player.
- Fixed route description button remain on screen after clearing the routes.
- Fixed do search if spaces are added to the filter.

### Issues
- Camera might freeze after spamming log recorder start stop.
- App works slow after a location with many wikipedia images is opened.
- App works slow in simulation/ navigation mode after you pan away from current position.
- Deleting a style after downloading it deletes the preview image as well.

## [2.0.0] - 2020-10-26
### Added
- Map styles as downloadable content with preview images.
- Custom car route.
- Custom public transport route.
- Navigate using GPS.
- Fly to current GPS position.
- Location details shows wikipedia images.
- Fly to search result.
- Speed limit view.
- Scrollable Main Menu window and Map Styles view.
- Dialog before exiting the application with the back button.
- Public transport route calculation and description.
- Log recorder and player.
- Simulation and navigation for predifined and custom routes.
- using PermissionsHelper.notifyOnPermissionsStatusChanged to notify permissions change.

### Changed
- Better layout for search items.
- Better layout for route description items.
- Better layout for the location details view.
- Hide toolbar at Fly to Route Instruction or Traffic.
- Providing pixelFormat and rotation to CanvasBufferRenderer.uploadFrame.

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
- Screen may turn off while recording.

## [Unreleased]
[1.5.0]: Update demo apps for SDK v7.1.20.43.693001B4
[1.4.0]: Update demo apps for SDK v7.1.20.42.AF307B1F
[1.3.0]: Update demo apps for SDK v7.1.20.41.37C8C713
[1.2.0]: Update demo apps for SDK v7.1.20.40.76465CE8
[1.1.0]: Update demo apps for SDK v7.1.20.39.5E1A0550
[1.0.0]: Initial release
