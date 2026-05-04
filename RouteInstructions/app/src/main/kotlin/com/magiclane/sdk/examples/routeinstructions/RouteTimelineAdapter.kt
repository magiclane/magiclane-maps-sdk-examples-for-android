/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeinstructions

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.routeinstructions.databinding.ListItemBinding
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.TrafficEvent
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall

class RouteTimelineAdapter(
    private val context: Context,
    private val route: Route,
    private val imageSize: Int,
    private val isDarkThemeOn: Boolean,
) : RecyclerView.Adapter<RouteTimelineAdapter.RouteInstructionViewHolder>() {

    private data class RouteTimelineItem(
        val bmp: Bitmap?,
        val text: String,
        val description: String,
        val distanceValue: String,
        val distanceUnit: String,
        val crossesRestrictedAreas: Boolean,
        val sortingKey: Int,
    )

    private val initialSortingKey = -10
    private val emptyValue = ""
    private val zeroDistanceText = "0.00"
    private val zeroText = "0"
    private val trafficSortingOffset = 1

    private val items = arrayListOf<RouteTimelineItem>()

    init {
        SdkCall.execute {
            var sortingKey = initialSortingKey
            var restrictedDistance = 0
            var restrictedTime = 0
            var routeLength = 0
            val routeInstructions = route.instructions

            route.timeDistance?.let { timeDistance ->
                val distanceInMeters = timeDistance.totalDistance
                val timeInSeconds = timeDistance.totalTime
                val description = getFormattedDistanceTime(distanceInMeters, timeInSeconds)

                routeLength = timeDistance.totalDistance

                restrictedDistance = timeDistance.restrictedDistance
                restrictedTime = timeDistance.restrictedTime

                items.add(
                    RouteTimelineItem(
                        bmp = getDrawableBitmap(
                            if (isDarkThemeOn) R.drawable.ic_baseline_route_24_night else R.drawable.ic_baseline_route_24,
                        ),
                        text = getRouteName(),
                        description = description,
                        distanceValue = emptyValue,
                        distanceUnit = emptyValue,
                        crossesRestrictedAreas = false,
                        sortingKey = sortingKey++,
                    ),
                )
            }

            if (route.hasTollRoads()) {
                addWarningItem(
                    iconRes = if (isDarkThemeOn) R.drawable.ic_toll_night else R.drawable.ic_toll_day,
                    text = context.getString(R.string.route_warning_tolls),
                    sortingKey = sortingKey++,
                )
            }

            if (route.hasFerryConnections()) {
                addWarningItem(
                    iconRes = if (isDarkThemeOn) R.drawable.ic_ferry_night else R.drawable.ic_ferry_day,
                    text = context.getString(R.string.route_warning_ferry),
                    sortingKey = sortingKey++,
                )
            }

            if ((restrictedDistance > 0) || (restrictedTime > 0)) {
                val description = getFormattedDistanceTime(restrictedDistance, restrictedTime)

                items.add(
                    RouteTimelineItem(
                        bmp = getDrawableBitmap(
                            if (isDarkThemeOn) R.drawable.ic_restricted_night else R.drawable.ic_restricted_day,
                        ),
                        text = context.getString(R.string.route_croseses_restricted_areas),
                        description = description,
                        distanceValue = emptyValue,
                        distanceUnit = emptyValue,
                        crossesRestrictedAreas = false,
                        sortingKey = sortingKey++,
                    ),
                )
            }

            for (routeInstruction in routeInstructions) {
                val aInner = if (isDarkThemeOn) Rgba(255, 255, 255, 255) else Rgba(0, 0, 0, 255)
                val aOuter = if (isDarkThemeOn) Rgba(0, 0, 0, 255) else Rgba(255, 255, 255, 255)
                val iInner = Rgba(128, 128, 128, 255)
                val iOuter = Rgba(128, 128, 128, 255)

                val bmp = if (routeInstruction.hasTurnInfo()) {
                    GemUtilImages.asBitmap(
                        routeInstruction.turnDetails?.abstractGeometryImage,
                        imageSize,
                        imageSize,
                        aInner,
                        aOuter,
                        iInner,
                        iOuter,
                    )
                } else {
                    null
                }

                var text = emptyValue
                var description = emptyValue
                var crossesRestrictedAreas = false

                if (routeInstruction.hasTurnInfo()) {
                    text = trimTrailingDot(routeInstruction.turnInstruction)
                }

                if (routeInstruction.hasFollowRoadInfo()) {
                    description = trimTrailingDot(routeInstruction.followRoadInstruction)
                }

                val distance = routeInstruction.traveledTimeDistance?.totalDistance ?: 0
                val distText = GemUtil.getDistText(distance, SdkSettings.unitSystem, true)
                val distValue = normalizeDistanceValue(distText.first)

                routeInstruction.timeDistanceToNextTurn?.let { timeDistToNextTurn ->
                    if ((timeDistToNextTurn.restrictedTime > 0) || (timeDistToNextTurn.restrictedDistance > 0)) {
                        crossesRestrictedAreas = true
                    }
                }

                items.add(
                    RouteTimelineItem(
                        bmp = bmp,
                        text = text,
                        description = description,
                        distanceValue = distValue,
                        distanceUnit = distText.second,
                        crossesRestrictedAreas = crossesRestrictedAreas,
                        sortingKey = distance,
                    ),
                )
            }

            route.trafficEvents?.let { trafficEvents ->
                for (trafficEvent in trafficEvents) {
                    val bmp = GemUtilImages.asBitmap(trafficEvent.image, imageSize, imageSize)
                    val text = formatTrafficDelayAndLength(trafficEvent)
                    val trafficEventDescription = trafficEvent.description ?: context.getString(R.string.traffic)
                    var description: String

                    val from = trafficEvent.fromLandmark
                    val to = trafficEvent.toLandmark

                    if ((from?.second == true) && (to?.second == true)) {
                        val strFrom = GemUtil.formatLandmarkDetails(from.first, true)
                        val strTo = GemUtil.formatLandmarkDetails(to.first, true)

                        val fromToDescription = if (strFrom.equals(strTo, true)) {
                            context.getString(R.string.on_road_name, strFrom)
                        } else {
                            context.getString(R.string.from_a_to_b, strFrom, strTo)
                        }

                        description = "$trafficEventDescription\n$fromToDescription"
                    } else {
                        description = trafficEventDescription
                    }

                    val remainingDistance = trafficEvent.distanceToDestination
                    var distance = routeLength - remainingDistance
                    if (distance < 0) {
                        distance = 0
                    }

                    val sortingKey = distance + trafficSortingOffset
                    val distText = GemUtil.getDistText(distance, EUnitSystem.Metric, true)
                    val distTextValue = normalizeDistanceValue(distText.first)

                    items.add(
                        RouteTimelineItem(
                            bmp = bmp,
                            text = text,
                            description = description,
                            distanceValue = distTextValue,
                            distanceUnit = distText.second,
                            crossesRestrictedAreas = false,
                            sortingKey = sortingKey,
                        ),
                    )
                }
            }

            items.sortBy { it.sortingKey }
        }
    }

    private fun getRouteName(): String {
        route.waypoints?.let { waypoints ->
            if (waypoints.size >= 2) {
                var departureName = waypoints[0].name ?: ""
                var destinationName = waypoints[waypoints.size - 1].name ?: ""

                if (departureName.isEmpty()) {
                    departureName = GemUtil.getFormattedWaypointName(waypoints[0])
                }

                if (destinationName.isEmpty()) {
                    destinationName = GemUtil.getFormattedWaypointName(waypoints[waypoints.size - 1])
                }

                return context.getString(R.string.from_a_to_b, departureName, destinationName)
            }
        }

        return ""
    }

    private fun getDrawableBitmap(iconRes: Int): Bitmap? {
        return ContextCompat.getDrawable(context, iconRes)?.toBitmap(imageSize, imageSize)
    }

    private fun addWarningItem(iconRes: Int, text: String, sortingKey: Int) {
        items.add(
            RouteTimelineItem(
                bmp = getDrawableBitmap(iconRes),
                text = text,
                description = emptyValue,
                distanceValue = emptyValue,
                distanceUnit = emptyValue,
                crossesRestrictedAreas = false,
                sortingKey = sortingKey,
            ),
        )
    }

    private fun trimTrailingDot(value: String?): String {
        val text = value.orEmpty()
        return if (text.endsWith(".")) text.removeSuffix(".") else text
    }

    private fun normalizeDistanceValue(value: String): String {
        return if (value == zeroDistanceText) zeroText else value
    }

    private fun formatTime(timeInSeconds: Int): String {
        val seconds = timeInSeconds % 60
        val timeInMinutes = timeInSeconds / 60
        val minutes = timeInMinutes % 60
        val hours = timeInMinutes / 60

        return if (timeInMinutes > 0) {
            context.resources.getString(R.string.time_format_hour, hours, minutes)
        } else if (minutes > 0) {
            context.resources.getString(R.string.time_format_min, minutes)
        } else {
            context.resources.getString(R.string.time_format_sec, seconds)
        }
    }

    private fun getFormattedDistanceTime(distanceInMeters: Int, timeInSeconds: Int): String {
        val distance = GemUtil.getDistText(distanceInMeters, EUnitSystem.Metric, true)
        val time = formatTime(timeInSeconds)

        return String.format("%s %s - %s", distance.first, distance.second, time)
    }

    private fun formatTrafficDelayAndLength(event: TrafficEvent): String {
        val distText = GemUtil.getDistText(event.length, EUnitSystem.Metric, true)

        return if (!event.isRoadblock) {
            val timeText = GemUtil.getTimeText(event.delay)
            String.format("%s %s, %s %s", timeText.first, timeText.second, distText.first, distText.second)
        } else {
            String.format("%s, %s %s", context.getString(R.string.roadblock), distText.first, distText.second)
        }
    }

    class RouteInstructionViewHolder(
        val binding: ListItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteInstructionViewHolder {
        val itemBinding = ListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return RouteInstructionViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: RouteInstructionViewHolder, position: Int) {
        val item = items[position]
        val descriptionColor = if (item.crossesRestrictedAreas) {
            ContextCompat.getColor(context, android.R.color.holo_red_dark)
        } else {
            ContextCompat.getColor(context, R.color.on_surface)
        }

        with(holder.binding) {
            turnImage.setImageBitmap(item.bmp)
            text.text = item.text
            description.text = item.description
            description.visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE
            description.setTextColor(descriptionColor)
            statusText.text = item.distanceValue
            statusDescription.text = item.distanceUnit
        }
    }

    override fun getItemCount() = items.size
}
