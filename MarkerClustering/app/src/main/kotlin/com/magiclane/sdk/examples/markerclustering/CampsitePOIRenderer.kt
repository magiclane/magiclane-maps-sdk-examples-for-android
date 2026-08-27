/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// Two-layer clustering renderer:
//
//   1. CLUSTER layer — all campsites, native grouping → density "pill" bubbles
//      WITH the count label. It has NO customMarkerSettings, because per-marker
//      settings carry no group-text fields and would suppress the cluster count.
//      Its loose (ungrouped) markers draw a transparent image.
//   2. DETAIL layer — the SAME campsites, grouped at the SAME zoom but with
//      TRANSPARENT group images (so it shows nothing while clustered), and
//      customMarkerSettings to colour each loose marker green/red.
//
// Because both layers hold the SAME marker set and group at the SAME zoom, they
// make IDENTICAL group/loose decisions, so a pill never sits on top of a loose
// pin and the count always comes from the cluster layer.
//
// Must be called on the SDK thread via `SdkCall.execute { }` (fire-and-forget) —
// NOT `runSynced`, which would hold the non-reentrant call lock for the whole
// build and starve map rendering / native finalizers.

package com.magiclane.sdk.examples.markerclustering

import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.d3scene.EMarkerLabelingMode
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.d3scene.MarkerRenderSettings
import com.magiclane.sdk.places.Coordinates

class CampsitePOIRenderer(private val icons: CampsiteMarkerIcons) {

    private val collectionName = "campsites"

    // Shared per-marker pins (created once → no per-callback native churn).
    private val bookablePin: MarkerRenderSettings by lazy {
        MarkerRenderSettings().apply {
            image = icons.makePin(bookable = true)
            imageSize = 6.4
        }
    }
    private val notBookablePin: MarkerRenderSettings by lazy {
        MarkerRenderSettings().apply {
            image = icons.makePin(bookable = false)
            imageSize = 6.4
        }
    }

    // Explicit Function1 OBJECT (not a lambda): the SDK's native code invokes it
    // by the specialised signature invoke(Marker): MarkerRenderSettings.
    private val perMarkerSettings = object : Function1<Marker?, MarkerRenderSettings> {
        override fun invoke(marker: Marker?): MarkerRenderSettings =
            if (marker?.name?.contains("\"bookable\":true") == true) bookablePin else notBookablePin
    }

    fun syncPOIs(mapView: MapView, markers: List<CampsiteMarkerDescriptor>, clusterMaxZoom: Double = 6.0) {
        val prefs = mapView.preferences?.markers ?: return
        prefs.clear()

        val clusterZoom = MapZoomConverter.toMagicLaneZoom(clusterMaxZoom)

        // 1) Cluster layer — density pills + count label. No customMarkerSettings.
        val clustered = MarkerCollection(EMarkerType.Point, "$collectionName-clustered")
        for (marker in markers) {
            clustered.add(markerOf(marker))
        }
        val clusterSettings = MarkerCollectionRenderSettings().apply {
            imageSize = 5.8
            labelTextSize = 0.0
            labelGroupTextColor = Rgba(255, 255, 255, 255)
            labelGroupTextSize = 2.7
            pointsGroupingZoomLevel = clusterZoom
            buildPointsGroupConfig = true
            lowDensityPointsGroupImage = icons.makePill(digits = 2)
            mediumDensityPointsGroupImage = icons.makePill(digits = 3)
            highDensityPointsGroupImage = icons.makePill(digits = 4)
            lowDensityPointsGroupMaxCount = 200
            mediumDensityPointsGroupMaxCount = 2000
            // Group | GroupCenter, deliberately WITHOUT FitImage.
            labelingMode = EMarkerLabelingMode.Group.value or EMarkerLabelingMode.GroupCenter.value
            image = icons.transparent() // loose singles invisible — detail layer draws them
        }
        prefs.add(clustered, clusterSettings)

        // 2) Detail layer — same full set, transparent groups, coloured loose pins.
        val detail = MarkerCollection(EMarkerType.Point, "$collectionName-detail")
        for (marker in markers) {
            detail.add(markerOf(marker))
        }
        val detailSettings = MarkerCollectionRenderSettings().apply {
            imageSize = 6.4
            labelTextSize = 0.0
            labelGroupTextSize = 0.0
            pointsGroupingZoomLevel = clusterZoom
            // buildPointsGroupConfig lets a tap on this (topmost) layer enumerate
            // the group's members via getPointsGroupHead/Components — needed so a
            // cluster tap can fit/list its campsites. Group images stay transparent,
            // so it still shows nothing while clustered.
            buildPointsGroupConfig = true
            lowDensityPointsGroupImage = icons.transparent()
            mediumDensityPointsGroupImage = icons.transparent()
            highDensityPointsGroupImage = icons.transparent()
            image = icons.transparent()
            setCustomMarkerSettings(perMarkerSettings)
        }
        prefs.add(detail, detailSettings)
    }

    private fun markerOf(descriptor: CampsiteMarkerDescriptor): Marker = Marker().apply {
        setCoordinates(arrayListOf(Coordinates(descriptor.latitude, descriptor.longitude)))
        name = descriptor.markerName
    }
}
