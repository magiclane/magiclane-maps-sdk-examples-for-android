/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.activities.publictransport

import android.graphics.Bitmap
import com.generalmagic.sdk.core.CommonSettings
import com.generalmagic.sdk.core.RectangleGeographicArea
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.core.Time
import com.generalmagic.sdk.demo.activities.RouteInstructionItem
import com.generalmagic.sdk.demo.util.Utils
import com.generalmagic.sdk.demo.util.Utils.Companion.getDistText
import com.generalmagic.sdk.routingandnavigation.*
import com.generalmagic.sdk.util.GEMSdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.StringIds

class TRouteDescriptionItem {
    var m_departureTime = ""
    var m_arrivalTime = ""
    var m_startStationName = ""
    var m_stopStationName = ""
    var m_stationEarlyTime = ""
    var m_stationLaterTime = ""
    var m_stationPlatform = ""
    var m_startStationPlatformCode = ""
    var m_endStationPlatformCode = ""
    var m_AtoBLineName = ""
    var m_toBLineName = ""
    var m_timeToNextStation = ""
    var m_distanceToNextStation = ""
    var m_supportLineInfo = ""
    var m_agencyName = ""
    var m_agencyURL = ""
    var m_agencyFareURL = ""
    var m_agencyPhone = ""
    var m_stayOnSameVehicle = ""
    val m_stopNames = ArrayList<String>()
    val m_instructionsList = ArrayList<RouteInstructionItem>()
    var m_geographicArea: RectangleGeographicArea? = null
    var m_canExpand = false
    var m_isExpanded = false
    var m_isWalk = false
    var m_isStationWalk = false
}

class TRouteSegmentItem {
    var m_type: ETransitType = ETransitType.ETTUnknown
    var m_iconId = 0
    var m_realTimeStatus = ERealtimeStatus.ERSNotAvailable
    var m_backgroundColor = GEMSdkCall.execute { Rgba(255, 255, 255, 255) }?.value() ?: 0
    var m_foregroundColor = GEMSdkCall.execute { Rgba(0, 0, 0, 255) }?.value() ?: 0
    var m_name = ""
    var m_travelTimeValue = ""
    var m_travelTimeUnit = ""
    var m_hasWheelChairSupport = false
    var m_hasBicycleSupport = false
    var m_visible = false
    var m_icon: Bitmap? = null
}

class TRouteItem {
    var m_nLeftImageId = -1
    var m_nRouteIndex = -1
    var m_tripTimeInterval = ""
    var m_tripDuration = ""
    var m_numberOfChanges = ""
    var m_tripSegments = ArrayList<TRouteSegmentItem>()
    var m_from = ""
    var m_walkingTime = ""
    var m_walkingDistance = ""
    var m_walkingInfo = ""
    var m_frequency = ""
    var m_fare = ""
    var m_warning = ""
    var m_departureTime = GEMSdkCall.execute { Time() }
    var m_arrivalTime = GEMSdkCall.execute { Time() }
    var m_departureTimeStr = ""
    var m_arrivalTimeStr = ""
}

data class TAgencyInfo(
    var m_name: String = "",
    var m_url: String = "",
    var m_fare_url: String = "",
    var m_phone: String = ""
)

class Helper {
    companion object {
        fun fillRouteDescriptionItems(
            route: Route,
            header: TRouteItem,
            routeDescriptionItems: ArrayList<TRouteDescriptionItem>
        ) {
            GEMSdkCall.checkCurrentThread()

            val routeSegmentsList = route.getSegments() ?: return
            val nSegmentsCount = routeSegmentsList.size
            var departureTime: Time
            var arrivalTime: Time

            val bUse24HourNotation = true
            val bWheelChairSupportRequested = false
            val bBicycleSupportRequested = false

            routeDescriptionItems.clear()

            for (nSegmentIndex in 0 until nSegmentsCount) {
                val routeSegment = routeSegmentsList[nSegmentIndex]
                val routeDescriptionItem = TRouteDescriptionItem()

                routeDescriptionItem.m_geographicArea = routeSegment.getGeographicArea()

                val instructionList = routeSegment.getInstructions() ?: ArrayList()
                val instructionsCount = instructionList.size

                if (routeSegment.getSegmentType() == ERouteSegmentType.ESTPublicTransport) {
                    departureTime = routeSegment.toPTRouteSegment()?.getDepartureTime() ?: Time()
                    arrivalTime = routeSegment.toPTRouteSegment()?.getArrivalTime() ?: Time()
                    var departureTimeHour = departureTime.getHour()
                    val departureTimeMinute = departureTime.getMinute()
                    var arrivalTimeHour = arrivalTime.getHour()
                    val arrivalTimeMinute = arrivalTime.getMinute()
                    var departureTimeUnit = ""
                    var arrivalTimeUnit = ""

                    if (!bUse24HourNotation) {
                        if ((departureTimeHour >= 12) && (departureTimeHour < 24)) {
                            departureTimeUnit = Utils.getUIString(StringIds.eStrPm)
                            if (departureTimeHour > 12) {
                                departureTimeHour -= 12
                            }
                        } else {
                            departureTimeUnit = Utils.getUIString(StringIds.eStrAm)
                        }

                        if ((arrivalTimeHour >= 12) && (arrivalTimeHour < 24)) {
                            arrivalTimeUnit = Utils.getUIString(StringIds.eStrPm)
                            if (arrivalTimeHour > 12) {
                                arrivalTimeHour -= 12
                            }
                        } else {
                            arrivalTimeUnit = Utils.getUIString(StringIds.eStrAm)
                        }
                    }

                    routeDescriptionItem.m_departureTime = String.format(
                        "%d:%02d",
                        departureTimeHour,
                        departureTimeMinute
                    )
                    routeDescriptionItem.m_arrivalTime = String.format(
                        "%d:%02d",
                        arrivalTimeHour,
                        arrivalTimeMinute
                    )

                    if (departureTimeUnit.isNotEmpty()) {
                        routeDescriptionItem.m_departureTime += " "
                        routeDescriptionItem.m_departureTime += departureTimeUnit
                    }

                    if (arrivalTimeUnit.isNotEmpty()) {
                        routeDescriptionItem.m_arrivalTime += " "
                        routeDescriptionItem.m_arrivalTime += arrivalTimeUnit
                    }

                    val from = routeSegment.toPTRouteSegment()?.getLineFrom() ?: ""
                    val to = routeSegment.toPTRouteSegment()?.getLineTowards() ?: ""

                    if (from.isNotEmpty() && to.isNotEmpty()) {
                        routeDescriptionItem.m_AtoBLineName =
                            String.format("%s towards %s", from, to)
                    } else {
                        routeDescriptionItem.m_AtoBLineName =
                            routeSegment.toPTRouteSegment()?.getName() ?: ""
                    }

                    if (to.isNotEmpty()) {
                        routeDescriptionItem.m_toBLineName = String.format(
                            Utils.getUIString(StringIds.eStrTowardsXLocation),
                            to
                        )
                    }

                    routeDescriptionItem.m_isWalk = false

                    val nTravelTime = (arrivalTime.asInt() - departureTime.asInt()) / 1000

                    var timeText = Utils.getTimeText(nTravelTime.toInt())
                    routeDescriptionItem.m_timeToNextStation = String.format(
                        "%s %s",
                        timeText.first,
                        timeText.second
                    )

                    var bFirstStationHasWheelChairSupport = false
                    var bLastStationHasWheelChairSupport = false

                    if (instructionsCount > 1) {
                        var tmp: String
                        if (instructionsCount == 2) {
                            tmp = String.format(
                                Utils.getUIString(StringIds.eStrNoOfStopsAndTime),
                                instructionsCount - 1,
                                routeDescriptionItem.m_timeToNextStation
                            )
                        } else {
                            tmp = String.format(
                                Utils.getUIString(StringIds.eStrNoOfStopsAndTime),
                                instructionsCount - 1,
                                routeDescriptionItem.m_timeToNextStation
                            )

                            routeDescriptionItem.m_canExpand = true
                            for (instructionIndex in 1 until instructionsCount - 1) {
                                val instrName =
                                    instructionList[instructionIndex].toPTRouteInstruction()
                                        ?.getName()

                                if (instrName != null) {
                                    routeDescriptionItem.m_stopNames.add(instrName)
                                }
                            }
                        }
                        routeDescriptionItem.m_timeToNextStation = tmp

                        bFirstStationHasWheelChairSupport =
                            instructionList[0].toPTRouteInstruction()?.getHasWheelchairSupport()
                                ?: false

                        val startPlatformCode =
                            instructionList[0].toPTRouteInstruction()?.getPlatformCode() ?: ""
                        if (startPlatformCode.isNotEmpty()) {
                            routeDescriptionItem.m_stationPlatform = String.format(
                                Utils.getUIString(StringIds.eStrPlatformNo),
                                startPlatformCode
                            )
                            routeDescriptionItem.m_startStationPlatformCode = startPlatformCode
                        }

                        bLastStationHasWheelChairSupport =
                            instructionList[instructionsCount - 1].toPTRouteInstruction()
                                ?.getHasWheelchairSupport() ?: false

                        val endPlatformCode =
                            instructionList[instructionsCount - 1].toPTRouteInstruction()
                                ?.getPlatformCode() ?: ""
                        if (endPlatformCode.isNotEmpty()) {
                            routeDescriptionItem.m_endStationPlatformCode = endPlatformCode
                        }
                    }

                    val departureDelayInSeconds =
                        routeSegment.toPTRouteSegment()?.getDepartureDelayInSeconds() ?: 0

                    if (departureDelayInSeconds != 0) {
                        if (departureDelayInSeconds < 0) {
                            timeText = Utils.getTimeText(-departureDelayInSeconds)
                            val tmp = String.format("%s %s", timeText.first, timeText.second)

                            routeDescriptionItem.m_stationEarlyTime = String.format(
                                Utils.getUIString(StringIds.eStrXTimeEarly),
                                tmp
                            )
                        } else {
                            timeText = Utils.getTimeText(departureDelayInSeconds)
                            val tmp = String.format("%s %s", timeText.first, timeText.second)

                            routeDescriptionItem.m_stationLaterTime = String.format(
                                Utils.getUIString(StringIds.eStrXTimeLate),
                                tmp
                            )
                        }
                    } else if (routeSegment.toPTRouteSegment()
                            ?.getRealtimeStatus() == ERealtimeStatus.ERSOnTime
                    ) {
                        routeDescriptionItem.m_stationEarlyTime =
                            Utils.getUIString(StringIds.eStrOnTime)
                    }

                    if (instructionsCount >= 2) {
                        routeDescriptionItem.m_startStationName =
                            instructionList[0].toPTRouteInstruction()?.getName() ?: ""
                        routeDescriptionItem.m_stopStationName =
                            instructionList[instructionsCount - 1].toPTRouteInstruction()?.getName()
                                ?: ""

                        if ((routeDescriptionItems.size > 0) && routeDescriptionItems.last().m_isWalk) {
                            routeDescriptionItems.last().m_stopStationName =
                                routeDescriptionItem.m_startStationName
                        }
                    }

                    if ((routeDescriptionItems.size > 0) && routeDescriptionItems.last().m_isWalk) {
                        routeDescriptionItems.last().m_arrivalTime =
                            routeDescriptionItem.m_departureTime
                    }

                    if (bWheelChairSupportRequested || bBicycleSupportRequested) {
                        val bRouteSegmentHasWheelchairSupport = routeSegment.toPTRouteSegment()
                            ?.getHasWheelchairSupport() ?: false && bFirstStationHasWheelChairSupport && bLastStationHasWheelChairSupport
                        val bRouteSegmentHasBicycleSupport =
                            routeSegment.toPTRouteSegment()?.getHasBicycleSupport() ?: false

                        if (bWheelChairSupportRequested && bBicycleSupportRequested) {
                            if (bRouteSegmentHasWheelchairSupport && bRouteSegmentHasBicycleSupport) {
                                routeDescriptionItem.m_supportLineInfo =
                                    Utils.getUIString(StringIds.eStrBicycleAndWheelchairSupport)
                            } else if (bRouteSegmentHasWheelchairSupport) {
                                routeDescriptionItem.m_supportLineInfo =
                                    Utils.getUIString(StringIds.eStrWheelchairSupport)
                            } else if (bRouteSegmentHasBicycleSupport) {
                                routeDescriptionItem.m_supportLineInfo =
                                    Utils.getUIString(StringIds.eStrBicycleSupport)
                            }
                        } else if (bWheelChairSupportRequested) {
                            if (bRouteSegmentHasWheelchairSupport) {
                                routeDescriptionItem.m_supportLineInfo =
                                    Utils.getUIString(StringIds.eStrWheelchairSupport)
                            }
                        } else // if (bBicycleSupportRequested)
                        {
                            if (bRouteSegmentHasBicycleSupport) {
                                routeDescriptionItem.m_supportLineInfo =
                                    Utils.getUIString(StringIds.eStrBicycleSupport)
                            }
                        }
                    }

                    routeDescriptionItem.m_agencyName =
                        routeSegment.toPTRouteSegment()?.getAgencyName() ?: ""
                    routeDescriptionItem.m_agencyURL =
                        routeSegment.toPTRouteSegment()?.getAgencyUrl() ?: ""
                    routeDescriptionItem.m_agencyFareURL =
                        routeSegment.toPTRouteSegment()?.getAgencyFareUrl() ?: ""
                    routeDescriptionItem.m_agencyPhone =
                        routeSegment.toPTRouteSegment()?.getAgencyPhone() ?: ""

                    if (routeSegment.toPTRouteSegment()?.getStayOnSameTransit() == true) {
                        routeDescriptionItem.m_stayOnSameVehicle =
                            Utils.getUIString(StringIds.eStrContinueOnSameVehicle)
                    }
                } else {
                    routeDescriptionItem.m_AtoBLineName = "Walk"
                    routeDescriptionItem.m_isWalk = true

                    if (nSegmentsCount > 1) {
                        val timeText = Utils.getTimeText(
                            routeSegment.getTimeDistance()?.getTotalTime() ?: 0
                        )
                        routeDescriptionItem.m_timeToNextStation =
                            "${timeText.first} ${timeText.second}"
                    } else {
                        routeDescriptionItem.m_timeToNextStation = header.m_walkingTime
                    }

                    val distanceText = getDistText(
                        routeSegment.getTimeDistance()?.getTotalDistance() ?: 0,
                        CommonSettings().getUnitSystem(),
                        true
                    )

                    routeDescriptionItem.m_distanceToNextStation =
                        "${distanceText.first} ${distanceText.second}"

                    if (nSegmentIndex == 0) {
                        val waypoints = route.getWaypoints() ?: ArrayList()
                        if (waypoints.isNotEmpty()) {
                            routeDescriptionItem.m_startStationName =
                                Utils.getFormattedWaypointName(waypoints[0])
                        }
                        routeDescriptionItem.m_departureTime = header.m_departureTimeStr
                    } else {
                        if ((routeDescriptionItems.size > 0) && !routeDescriptionItems.last().m_isWalk) {
                            routeDescriptionItem.m_startStationName =
                                routeDescriptionItems.last().m_stopStationName
                            routeDescriptionItem.m_departureTime =
                                routeDescriptionItems.last().m_arrivalTime
                        }
                    }

                    if (nSegmentIndex == (nSegmentsCount - 1)) {
                        val waypoints = route.getWaypoints() ?: ArrayList()
                        if (waypoints.isNotEmpty()) {
                            routeDescriptionItem.m_stopStationName =
                                Utils.getFormattedWaypointName(waypoints.last())
                        }

                        routeDescriptionItem.m_arrivalTime = header.m_arrivalTimeStr
                    }

                    fillRouteInstructionsList(routeSegment, routeDescriptionItem.m_instructionsList)

                    routeDescriptionItem.m_isStationWalk =
                        (routeSegment.getSegmentType() == ERouteSegmentType.ESTStationWalk)
                    routeDescriptionItem.m_canExpand =
                        (routeDescriptionItem.m_instructionsList.size > 0)
                }

                routeDescriptionItems.add(routeDescriptionItem)
            }

            for (i in 1 until routeDescriptionItems.size - 1) {
                val item = routeDescriptionItems[i]
                if (item.m_isStationWalk) {
                    val prevItem = routeDescriptionItems[i - 1]
                    val nextItem = routeDescriptionItems[i + 1]

                    if (!prevItem.m_isWalk && !nextItem.m_isWalk && nextItem.m_startStationPlatformCode.isNotEmpty()) {
                        if (prevItem.m_endStationPlatformCode.isNotEmpty()) {
                            item.m_stationPlatform = String.format(
                                Utils.getUIString(StringIds.eStrChangeFromPlatformNoToPlatformNo),
                                prevItem.m_endStationPlatformCode,
                                nextItem.m_startStationPlatformCode
                            )
                        } else {
                            item.m_stationPlatform = String.format(
                                Utils.getUIString(StringIds.eStrChangeToPlatformNo),
                                nextItem.m_startStationPlatformCode
                            )
                        }
                    }
                }
            }
        }

        fun fillRouteInstructionsList(
            routeSegment: RouteSegment,
            routeInstructionsList: ArrayList<RouteInstructionItem>
        ) {
            GEMSdkCall.checkCurrentThread()

            routeInstructionsList.clear()

            if (routeSegment.getSegmentType() == ERouteSegmentType.ESTRoute) {
                val instructionList = routeSegment.getInstructions() ?: ArrayList()

                if (instructionList.size == 0) {
                    return
                }

                val distance = instructionList[0].getTraveledTimeDistance()?.getTotalDistance() ?: 0

                for (a in instructionList) {
                    routeInstructionsList.add(RouteInstructionItem(a, distance.toDouble()))
                }
            }
        }

        fun getIconId(type: ETransitType): Int {
            when (type) {
                ETransitType.ETTBus -> {
                    return SdkIcons.PublicTransport.PublicTransport_Bus.value
                }
                ETransitType.ETTUnderground -> {
                    return SdkIcons.PublicTransport.PublicTransport_Underground.value
                }
                ETransitType.ETTRailway -> {
                    return SdkIcons.PublicTransport.PublicTransport_Train.value
                }
                ETransitType.ETTTram -> {
                    return SdkIcons.PublicTransport.PublicTransport_Tram.value
                }
                ETransitType.ETTWaterTransport -> {
                    return SdkIcons.PublicTransport.PublicTransport_Water.value
                }
                ETransitType.ETTOther -> {
                    return SdkIcons.PublicTransport.PublicTransport_Other.value
                }
                ETransitType.ETTWalk -> {
                    return SdkIcons.PublicTransport.PublicTransport_Walk.value
                }
                else -> {
                }
            }
            return -1
        }
    }
}
