/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused")

package com.generalmagic.sdk.examples.demo.activities.publictransport

import android.graphics.Bitmap
import com.generalmagic.sdk.core.RectangleGeographicArea
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.core.Time
import com.generalmagic.sdk.examples.demo.activities.RouteInstructionItem
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.UtilUiTexts.getDistText
import com.generalmagic.sdk.util.UtilUiTexts.getTimeText
import com.generalmagic.sdk.util.UtilUiTexts.getUIString

class TRouteDescriptionItem {
    var mDepartureTime = ""
    var mArrivalTime = ""
    var mStartStationName = ""
    var mStopStationName = ""
    var mStationEarlyTime = ""
    var mStationLaterTime = ""
    var mStationPlatform = ""
    var mStartStationPlatformCode = ""
    var mEndStationPlatformCode = ""
    var mAtoBLineName = ""
    var mToBLineName = ""
    var mTimeToNextStation = ""
    var mDistanceToNextStation = ""
    var mSupportLineInfo = ""
    var mAgencyName = ""
    var mAgencyURL = ""
    var mAgencyFareURL = ""
    var mAgencyPhone = ""
    var mStayOnSameVehicle = ""
    val mStopNames = ArrayList<String>()
    val mInstructionsList = ArrayList<RouteInstructionItem>()
    var mGeographicArea: RectangleGeographicArea? = null
    var mCanExpand = false
    var mIsExpanded = false
    var mIsWalk = false
    var mIsStationWalk = false
}

class TRouteSegmentItem {
    var mType: ETransitType = ETransitType.Unknown
    var mIconId = 0
    var mRealTimeStatus = ERealtimeStatus.NotAvailable
    var mBackgroundColor = SdkCall.execute { Rgba(255, 255, 255, 255) }?.value() ?: 0
    var mForegroundColor = SdkCall.execute { Rgba(0, 0, 0, 255) }?.value() ?: 0
    var mName = ""
    var mTravelTimeValue = ""
    var mTravelTimeUnit = ""
    var mHasWheelChairSupport = false
    var mHasBicycleSupport = false
    var mVisible = false
    var mIcon: Bitmap? = null
}

class TRouteItem {
    var mNLeftImageId = -1
    var mNRouteIndex = -1
    var mTripTimeInterval = ""
    var mTripDuration = ""
    var mNumberOfChanges = ""
    var mTripSegments = ArrayList<TRouteSegmentItem>()
    var mFrom = ""
    var mWalkingTime = ""
    var mWalkingDistance = ""
    var mWalkingInfo = ""
    var mFrequency = ""
    var mFare = ""
    var mWarning = ""
    var mDepartureTime = SdkCall.execute { Time() }
    var mArrivalTime = SdkCall.execute { Time() }
    var mDepartureTimeStr = ""
    var mArrivalTimeStr = ""
}

data class TAgencyInfo(
    var mName: String = "",
    var mUrl: String = "",
    var mFare_url: String = "",
    var mPhone: String = ""
)

class Helper {
    companion object {
        fun fillRouteDescriptionItems(
            route: Route,
            header: TRouteItem,
            routeDescriptionItems: ArrayList<TRouteDescriptionItem>
        ) {
            SdkCall.checkCurrentThread()

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

                routeDescriptionItem.mGeographicArea = routeSegment.getGeographicArea()

                val instructionList = routeSegment.getInstructions() ?: ArrayList()
                val instructionsCount = instructionList.size

                if (routeSegment.getSegmentType() == ERouteSegmentType.PublicTransport) {
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
                            departureTimeUnit = getUIString(StringIds.eStrPm)
                            if (departureTimeHour > 12) {
                                departureTimeHour -= 12
                            }
                        } else {
                            departureTimeUnit = getUIString(StringIds.eStrAm)
                        }

                        if ((arrivalTimeHour >= 12) && (arrivalTimeHour < 24)) {
                            arrivalTimeUnit = getUIString(StringIds.eStrPm)
                            if (arrivalTimeHour > 12) {
                                arrivalTimeHour -= 12
                            }
                        } else {
                            arrivalTimeUnit = getUIString(StringIds.eStrAm)
                        }
                    }

                    routeDescriptionItem.mDepartureTime = String.format(
                        "%d:%02d",
                        departureTimeHour,
                        departureTimeMinute
                    )
                    routeDescriptionItem.mArrivalTime = String.format(
                        "%d:%02d",
                        arrivalTimeHour,
                        arrivalTimeMinute
                    )

                    if (departureTimeUnit.isNotEmpty()) {
                        routeDescriptionItem.mDepartureTime += " "
                        routeDescriptionItem.mDepartureTime += departureTimeUnit
                    }

                    if (arrivalTimeUnit.isNotEmpty()) {
                        routeDescriptionItem.mArrivalTime += " "
                        routeDescriptionItem.mArrivalTime += arrivalTimeUnit
                    }

                    val from = routeSegment.toPTRouteSegment()?.getLineFrom() ?: ""
                    val to = routeSegment.toPTRouteSegment()?.getLineTowards() ?: ""

                    if (from.isNotEmpty() && to.isNotEmpty()) {
                        routeDescriptionItem.mAtoBLineName =
                            String.format("%s towards %s", from, to)
                    } else {
                        routeDescriptionItem.mAtoBLineName =
                            routeSegment.toPTRouteSegment()?.getName() ?: ""
                    }

                    if (to.isNotEmpty()) {
                        routeDescriptionItem.mToBLineName = String.format(
                            getUIString(StringIds.eStrTowardsXLocation),
                            to
                        )
                    }

                    routeDescriptionItem.mIsWalk = false

                    val nTravelTime = (arrivalTime.asInt() - departureTime.asInt()) / 1000

                    var timeText = getTimeText(nTravelTime.toInt())
                    routeDescriptionItem.mTimeToNextStation = String.format(
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
                                getUIString(StringIds.eStrNoOfStopsAndTime),
                                instructionsCount - 1,
                                routeDescriptionItem.mTimeToNextStation
                            )
                        } else {
                            tmp = String.format(
                                getUIString(StringIds.eStrNoOfStopsAndTime),
                                instructionsCount - 1,
                                routeDescriptionItem.mTimeToNextStation
                            )

                            routeDescriptionItem.mCanExpand = true
                            for (instructionIndex in 1 until instructionsCount - 1) {
                                val instrName =
                                    instructionList[instructionIndex].toPTRouteInstruction()
                                        ?.getName()

                                if (instrName != null) {
                                    routeDescriptionItem.mStopNames.add(instrName)
                                }
                            }
                        }
                        routeDescriptionItem.mTimeToNextStation = tmp

                        bFirstStationHasWheelChairSupport =
                            instructionList[0].toPTRouteInstruction()?.getHasWheelchairSupport()
                                ?: false

                        val startPlatformCode =
                            instructionList[0].toPTRouteInstruction()?.getPlatformCode() ?: ""
                        if (startPlatformCode.isNotEmpty()) {
                            routeDescriptionItem.mStationPlatform = String.format(
                                getUIString(StringIds.eStrPlatformNo),
                                startPlatformCode
                            )
                            routeDescriptionItem.mStartStationPlatformCode = startPlatformCode
                        }

                        bLastStationHasWheelChairSupport =
                            instructionList[instructionsCount - 1].toPTRouteInstruction()
                                ?.getHasWheelchairSupport() ?: false

                        val endPlatformCode =
                            instructionList[instructionsCount - 1].toPTRouteInstruction()
                                ?.getPlatformCode() ?: ""
                        if (endPlatformCode.isNotEmpty()) {
                            routeDescriptionItem.mEndStationPlatformCode = endPlatformCode
                        }
                    }

                    val departureDelayInSeconds =
                        routeSegment.toPTRouteSegment()?.getDepartureDelayInSeconds() ?: 0

                    if (departureDelayInSeconds != 0) {
                        if (departureDelayInSeconds < 0) {
                            timeText = getTimeText(-departureDelayInSeconds)
                            val tmp = String.format("%s %s", timeText.first, timeText.second)

                            routeDescriptionItem.mStationEarlyTime = String.format(
                                getUIString(StringIds.eStrXTimeEarly),
                                tmp
                            )
                        } else {
                            timeText = getTimeText(departureDelayInSeconds)
                            val tmp = String.format("%s %s", timeText.first, timeText.second)

                            routeDescriptionItem.mStationLaterTime = String.format(
                                getUIString(StringIds.eStrXTimeLate),
                                tmp
                            )
                        }
                    } else if (routeSegment.toPTRouteSegment()
                            ?.getRealtimeStatus() == ERealtimeStatus.OnTime
                    ) {
                        routeDescriptionItem.mStationEarlyTime =
                            getUIString(StringIds.eStrOnTime)
                    }

                    if (instructionsCount >= 2) {
                        routeDescriptionItem.mStartStationName =
                            instructionList[0].toPTRouteInstruction()?.getName() ?: ""
                        routeDescriptionItem.mStopStationName =
                            instructionList[instructionsCount - 1].toPTRouteInstruction()?.getName()
                                ?: ""

                        if ((routeDescriptionItems.size > 0) && routeDescriptionItems.last().mIsWalk) {
                            routeDescriptionItems.last().mStopStationName =
                                routeDescriptionItem.mStartStationName
                        }
                    }

                    if ((routeDescriptionItems.size > 0) && routeDescriptionItems.last().mIsWalk) {
                        routeDescriptionItems.last().mArrivalTime =
                            routeDescriptionItem.mDepartureTime
                    }

                    if (bWheelChairSupportRequested || bBicycleSupportRequested) {
                        val bRouteSegmentHasWheelchairSupport = routeSegment.toPTRouteSegment()
                            ?.getHasWheelchairSupport() ?: false && bFirstStationHasWheelChairSupport && bLastStationHasWheelChairSupport
                        val bRouteSegmentHasBicycleSupport =
                            routeSegment.toPTRouteSegment()?.getHasBicycleSupport() ?: false

                        if (bWheelChairSupportRequested && bBicycleSupportRequested) {
                            if (bRouteSegmentHasWheelchairSupport && bRouteSegmentHasBicycleSupport) {
                                routeDescriptionItem.mSupportLineInfo =
                                    getUIString(StringIds.eStrBicycleAndWheelchairSupport)
                            } else if (bRouteSegmentHasWheelchairSupport) {
                                routeDescriptionItem.mSupportLineInfo =
                                    getUIString(StringIds.eStrWheelchairSupport)
                            } else if (bRouteSegmentHasBicycleSupport) {
                                routeDescriptionItem.mSupportLineInfo =
                                    getUIString(StringIds.eStrBicycleSupport)
                            }
                        } else if (bWheelChairSupportRequested) {
                            if (bRouteSegmentHasWheelchairSupport) {
                                routeDescriptionItem.mSupportLineInfo =
                                    getUIString(StringIds.eStrWheelchairSupport)
                            }
                        } else // if (bBicycleSupportRequested)
                        {
                            if (bRouteSegmentHasBicycleSupport) {
                                routeDescriptionItem.mSupportLineInfo =
                                    getUIString(StringIds.eStrBicycleSupport)
                            }
                        }
                    }

                    routeDescriptionItem.mAgencyName =
                        routeSegment.toPTRouteSegment()?.getAgencyName() ?: ""
                    routeDescriptionItem.mAgencyURL =
                        routeSegment.toPTRouteSegment()?.getAgencyUrl() ?: ""
                    routeDescriptionItem.mAgencyFareURL =
                        routeSegment.toPTRouteSegment()?.getAgencyFareUrl() ?: ""
                    routeDescriptionItem.mAgencyPhone =
                        routeSegment.toPTRouteSegment()?.getAgencyPhone() ?: ""

                    if (routeSegment.toPTRouteSegment()?.getStayOnSameTransit() == true) {
                        routeDescriptionItem.mStayOnSameVehicle =
                            getUIString(StringIds.eStrContinueOnSameVehicle)
                    }
                } else {
                    routeDescriptionItem.mAtoBLineName = "Walk"
                    routeDescriptionItem.mIsWalk = true

                    if (nSegmentsCount > 1) {
                        val timeText = getTimeText(
                            routeSegment.getTimeDistance()?.getTotalTime() ?: 0
                        )
                        routeDescriptionItem.mTimeToNextStation =
                            "${timeText.first} ${timeText.second}"
                    } else {
                        routeDescriptionItem.mTimeToNextStation = header.mWalkingTime
                    }

                    val distanceText = getDistText(
                        routeSegment.getTimeDistance()?.getTotalDistance() ?: 0,
                        SdkSettings.getUnitSystem(),
                        true
                    )

                    routeDescriptionItem.mDistanceToNextStation =
                        "${distanceText.first} ${distanceText.second}"

                    if (nSegmentIndex == 0) {
                        val waypoints = route.getWaypoints() ?: ArrayList()
                        if (waypoints.isNotEmpty()) {
                            routeDescriptionItem.mStartStationName =
                                Utils.getFormattedWaypointName(waypoints[0])
                        }
                        routeDescriptionItem.mDepartureTime = header.mDepartureTimeStr
                    } else {
                        if ((routeDescriptionItems.size > 0) && !routeDescriptionItems.last().mIsWalk) {
                            routeDescriptionItem.mStartStationName =
                                routeDescriptionItems.last().mStopStationName
                            routeDescriptionItem.mDepartureTime =
                                routeDescriptionItems.last().mArrivalTime
                        }
                    }

                    if (nSegmentIndex == (nSegmentsCount - 1)) {
                        val waypoints = route.getWaypoints() ?: ArrayList()
                        if (waypoints.isNotEmpty()) {
                            routeDescriptionItem.mStopStationName =
                                Utils.getFormattedWaypointName(waypoints.last())
                        }

                        routeDescriptionItem.mArrivalTime = header.mArrivalTimeStr
                    }

                    fillRouteInstructionsList(routeSegment, routeDescriptionItem.mInstructionsList)

                    routeDescriptionItem.mIsStationWalk =
                        (routeSegment.getSegmentType() == ERouteSegmentType.StationWalk)
                    routeDescriptionItem.mCanExpand =
                        (routeDescriptionItem.mInstructionsList.size > 0)
                }

                routeDescriptionItems.add(routeDescriptionItem)
            }

            for (i in 1 until routeDescriptionItems.size - 1) {
                val item = routeDescriptionItems[i]
                if (item.mIsStationWalk) {
                    val prevItem = routeDescriptionItems[i - 1]
                    val nextItem = routeDescriptionItems[i + 1]

                    if (!prevItem.mIsWalk && !nextItem.mIsWalk && nextItem.mStartStationPlatformCode.isNotEmpty()) {
                        if (prevItem.mEndStationPlatformCode.isNotEmpty()) {
                            item.mStationPlatform = String.format(
                                getUIString(StringIds.eStrChangeFromPlatformNoToPlatformNo),
                                prevItem.mEndStationPlatformCode,
                                nextItem.mStartStationPlatformCode
                            )
                        } else {
                            item.mStationPlatform = String.format(
                                getUIString(StringIds.eStrChangeToPlatformNo),
                                nextItem.mStartStationPlatformCode
                            )
                        }
                    }
                }
            }
        }

        private fun fillRouteInstructionsList(
            routeSegment: RouteSegment,
            routeInstructionsList: ArrayList<RouteInstructionItem>
        ) {
            SdkCall.checkCurrentThread()

            routeInstructionsList.clear()

            if (routeSegment.getSegmentType() == ERouteSegmentType.Route) {
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
                ETransitType.Bus -> {
                    return SdkIcons.PublicTransport.PublicTransport_Bus.value
                }
                ETransitType.Underground -> {
                    return SdkIcons.PublicTransport.PublicTransport_Underground.value
                }
                ETransitType.Railway -> {
                    return SdkIcons.PublicTransport.PublicTransport_Train.value
                }
                ETransitType.Tram -> {
                    return SdkIcons.PublicTransport.PublicTransport_Tram.value
                }
                ETransitType.WaterTransport -> {
                    return SdkIcons.PublicTransport.PublicTransport_Water.value
                }
                ETransitType.Other -> {
                    return SdkIcons.PublicTransport.PublicTransport_Other.value
                }
                ETransitType.Walk -> {
                    return SdkIcons.PublicTransport.PublicTransport_Walk.value
                }
                else -> {
                }
            }
            return -1
        }
    }
}
