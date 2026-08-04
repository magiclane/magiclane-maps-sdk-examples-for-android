/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.examples.publictransitrouting.databinding.ActivityDescriptionBinding
import com.magiclane.sdk.routesandnavigation.ETransitType
import com.magiclane.sdk.routesandnavigation.RouteInstruction
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

/** Agency operating one or more transit legs of the route. */
class AgencyItem(val name: String, val phone: String, val url: String)

/** One turn-by-turn instruction of a pedestrian segment, pre-rendered for the UI thread. */
class WalkInstructionItem(
    val icon: Bitmap?,
    val text: String,
    val descriptionText: String,
    // Distance from the start of the walk to this instruction.
    val distanceText: String,
)

/** One row of the route description list. */
sealed class DescriptionRow {
    class Header(val item: PTRouteItem) : DescriptionRow()

    class Segment(
        val segmentIndex: Int,
        val transitType: ETransitType,
        val isWalk: Boolean,
        // Walk/transit style of the leg arriving at this station; null on the first station,
        // which has no rail line above its ring.
        val prevIsWalk: Boolean?,
        val departTimeText: String,
        val stationName: String,
        val badgeText: String,
        val badgeColor: Int,
        val badgeTextColor: Int,
        val towardsText: String,
        val platformText: String,
        val stopsText: String,
        val intermediateStops: List<String>,
        val walkInfoText: String,
        val walkInstructions: List<WalkInstructionItem>,
        val stayOnVehicle: Boolean,
        var expanded: Boolean = false,
    ) : DescriptionRow()

    class Arrival(
        val arriveTimeText: String,
        val destinationName: String,
        val prevIsWalk: Boolean?,
    ) : DescriptionRow()

    class AgencyInfo(val agencies: List<AgencyItem>) : DescriptionRow()
}

/**
 * Route description of the selected public-transit route: a header with the trip summary
 * followed by one row per segment. Tapping a segment flies the map to it and returns to the map.
 */
class PTRouteDescriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDescriptionBinding
    private lateinit var adapter: DescriptionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivityDescriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        val routeIndex = PTRouteSession.selectedRouteIndex
        adapter = DescriptionAdapter(
            onSegmentTap = { segmentIndex ->
                // Fly the map to the tapped segment and go back to it (as in Magic Earth).
                PTRouteSession.controller?.flyToSegment(routeIndex, segmentIndex)
                finish()
            },
            onAgencyTap = { agencies -> showAgencyInfo(agencies) },
        )
        binding.descriptionList.adapter = adapter

        loadRows(routeIndex)
    }

    private fun loadRows(routeIndex: Int) = SdkCall.execute {
        val rows = buildRows(routeIndex)
        Util.postOnMain {
            if (!isFinishing && !isDestroyed) adapter.submit(rows)
        }
    }

    /** Builds the description rows; must be called on the SDK thread. */
    private fun buildRows(routeIndex: Int): List<DescriptionRow> {
        val route = PTRouteSession.routes.getOrNull(routeIndex) ?: return emptyList()
        val item = PTRouteSession.items.getOrNull(routeIndex) ?: return emptyList()
        val segments = route.segments ?: return emptyList()

        val rows = mutableListOf<DescriptionRow>(DescriptionRow.Header(item))

        val waypoints = route.waypoints
        val firstWaypointName = waypoints?.firstOrNull()?.name.orEmpty()
        val lastWaypointName = waypoints?.lastOrNull()?.name.orEmpty()

        var previousArrivalStation = firstWaypointName
        // Leg style of the rail line arriving at the next station row (null before the first).
        var previousLegIsWalk: Boolean? = null
        val agencies = mutableListOf<AgencyItem>()

        segments.forEachIndexed { index, segment ->
            val ptSegment = segment.toPTRouteSegment() ?: return@forEachIndexed
            val isWalk = !segment.isCommon()

            // Insignificant intermediate walk legs are not worth a row.
            if (isWalk && !ptSegment.isSignificant() && index != 0 && index != segments.lastIndex) {
                return@forEachIndexed
            }

            val instructions = segment.instructions
            val firstInstruction = instructions?.firstOrNull()?.toPTRouteInstruction()
            val lastInstruction = instructions?.lastOrNull()?.toPTRouteInstruction()

            if (isWalk) {
                val timeDistance = segment.timeDistance
                val distance = timeDistance?.totalDistance ?: 0
                val time = timeDistance?.totalTime ?: 0

                rows.add(
                    DescriptionRow.Segment(
                        segmentIndex = index,
                        transitType = ETransitType.Walk,
                        isWalk = true,
                        prevIsWalk = previousLegIsWalk,
                        departTimeText = Formatters.timeText(ptSegment.departureTime),
                        stationName = previousArrivalStation,
                        badgeText = "",
                        badgeColor = 0,
                        badgeTextColor = 0,
                        towardsText = "",
                        platformText = "",
                        stopsText = "",
                        intermediateStops = emptyList(),
                        walkInfoText = if (distance > 0) {
                            "${Formatters.distanceText(this, distance)} (${Formatters.durationText(this, time)})"
                        } else {
                            ""
                        },
                        // Only significant walks expand into turn-by-turn instructions (as in
                        // Magic Earth); station walks and other tiny legs stay plain rows.
                        walkInstructions = if (ptSegment.isSignificant()) {
                            buildWalkInstructions(instructions)
                        } else {
                            emptyList()
                        },
                        stayOnVehicle = false,
                    ),
                )
                previousLegIsWalk = true
            } else {
                val departureTime = ptSegment.departureTime
                val arrivalTime = ptSegment.arrivalTime

                val instructionCount = instructions?.size ?: 0
                val hops = instructionCount - 1
                val rideSeconds = if (departureTime != null && arrivalTime != null) {
                    ((arrivalTime.asLong() - departureTime.asLong()) / 1000L).toInt().coerceAtLeast(0)
                } else {
                    0
                }
                val intermediateStops = if (instructionCount > 2) {
                    instructions.orEmpty().subList(1, instructionCount - 1)
                        .mapNotNull { it.toPTRouteInstruction()?.name }
                } else {
                    emptyList()
                }

                rows.add(
                    DescriptionRow.Segment(
                        segmentIndex = index,
                        transitType = ptSegment.transitType,
                        isWalk = false,
                        prevIsWalk = previousLegIsWalk,
                        departTimeText = Formatters.timeText(departureTime),
                        stationName = firstInstruction?.name ?: previousArrivalStation,
                        badgeText = ptSegment.shortName.orEmpty(),
                        badgeColor = Formatters.colorOf(ptSegment.lineColor),
                        badgeTextColor = Formatters.colorOf(ptSegment.lineTextColor),
                        towardsText = ptSegment.lineTowards
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { getString(R.string.towards_location, it) }
                            .orEmpty(),
                        platformText = firstInstruction?.platformCode
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { getString(R.string.platform_no, it) }
                            .orEmpty(),
                        stopsText = if (hops > 0) {
                            resources.getQuantityString(
                                R.plurals.stops_and_time,
                                hops,
                                hops,
                                Formatters.durationText(this, rideSeconds),
                            )
                        } else {
                            ""
                        },
                        intermediateStops = intermediateStops,
                        walkInfoText = "",
                        walkInstructions = emptyList(),
                        stayOnVehicle = ptSegment.stayOnSameTransit(),
                    ),
                )

                previousArrivalStation = lastInstruction?.name.orEmpty()
                previousLegIsWalk = false

                val agencyName = ptSegment.agencyName.orEmpty()
                if (agencyName.isNotEmpty() && agencies.none { it.name == agencyName }) {
                    agencies.add(
                        AgencyItem(
                            name = agencyName,
                            phone = ptSegment.agencyPhone.orEmpty(),
                            url = ptSegment.agencyUrl.orEmpty(),
                        ),
                    )
                }
            }
        }

        val lastArrivalTime = segments.lastOrNull()?.toPTRouteSegment()?.arrivalTime
        rows.add(
            DescriptionRow.Arrival(
                arriveTimeText = Formatters.timeText(lastArrivalTime),
                destinationName = lastWaypointName,
                prevIsWalk = previousLegIsWalk,
            ),
        )

        if (agencies.isNotEmpty()) {
            rows.add(DescriptionRow.AgencyInfo(agencies))
        }

        return rows
    }

    /**
     * The turn-by-turn instructions of a pedestrian segment, with the turn image rendered for
     * the current theme; must be called on the SDK thread.
     */
    private fun buildWalkInstructions(instructions: ArrayList<RouteInstruction>?): List<WalkInstructionItem> {
        if (instructions.isNullOrEmpty()) return emptyList()

        val iconSize = resources.getDimensionPixelSize(R.dimen.walk_instruction_icon_size)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val activeInner = if (isNightMode) Rgba(255, 255, 255, 255) else Rgba(0, 0, 0, 255)
        val activeOuter = if (isNightMode) Rgba(0, 0, 0, 255) else Rgba(255, 255, 255, 255)
        val inactive = Rgba(128, 128, 128, 255)

        // Instruction distances are traveled distances on the whole route; the first one is
        // subtracted so each row shows the distance from the start of the walk.
        val distanceOffset = instructions.first().traveledTimeDistance?.totalDistance ?: 0

        return instructions.map { instruction ->
            val traveled = instruction.traveledTimeDistance?.totalDistance ?: 0
            WalkInstructionItem(
                icon = if (instruction.hasTurnInfo()) {
                    GemUtilImages.asBitmap(
                        instruction.turnDetails?.abstractGeometryImage,
                        iconSize,
                        iconSize,
                        activeInner,
                        activeOuter,
                        inactive,
                        inactive,
                    )
                } else {
                    null
                },
                text = if (instruction.hasTurnInfo()) {
                    instruction.turnInstruction.orEmpty().trimEnd('.')
                } else {
                    ""
                },
                descriptionText = if (instruction.hasFollowRoadInfo()) {
                    instruction.followRoadInstruction.orEmpty().trimEnd('.')
                } else {
                    ""
                },
                distanceText = Formatters.distanceText(this, (traveled - distanceOffset).coerceAtLeast(0)),
            )
        }
    }

    /** Shows the agencies operating the route's transit lines, with tappable links. */
    private fun showAgencyInfo(agencies: List<AgencyItem>) {
        val message = agencies.joinToString("\n\n") { agency ->
            listOf(agency.name, agency.phone, agency.url).filter { it.isNotEmpty() }.joinToString("\n")
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agency_info)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()

        dialog.findViewById<TextView>(android.R.id.message)?.let { view ->
            Linkify.addLinks(view, Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)
            view.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
