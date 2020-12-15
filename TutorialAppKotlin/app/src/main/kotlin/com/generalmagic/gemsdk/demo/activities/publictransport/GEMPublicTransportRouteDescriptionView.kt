/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.publictransport

import android.graphics.Bitmap
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.util.AppUtils
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getUIString
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.Image
import com.generalmagic.gemsdk.models.ImageDatabase
import com.generalmagic.gemsdk.models.Time
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.GemIcons
import java.util.*
import kotlin.collections.ArrayList

object GEMPublicTransportRouteDescriptionView {

    internal lateinit var routeItem: RouteItem
    internal var separatorIcon: Bitmap? = null
    internal lateinit var title: String
    internal lateinit var agencyText: String
    internal var itemsCount = -1

    var m_nItems = 0
    var m_header = TRouteItem()
    var m_agencies = ArrayList<TAgencyInfo>()
    var m_routeDescriptionItems = ArrayList<TRouteDescriptionItem>()

    internal class RouteInstructionItem {
        var icon: Bitmap? = null
        var simpleText: String? = null
        var detailText: String? = null
        var simpleStatusText: String? = null
        var detailStatusText: String? = null
    }

    internal class RouteSegmentItem {
        var backgroundColor: Int = 0
        var foregroundColor: Int = 0
        var name = ""
        var travelTimeValue = ""
        var travelTimeUnit = ""
        var visible: Boolean = false
        var icon: Bitmap? = null
        var departureTime = ""
        var arrivalTime = ""
        var startStationName = ""
        var stopStationName = ""
        var stationTimeInfo = ""
        var stationTimeInfoColor: Int = 0
        var stationPlatform = ""
        var toBLineName = ""
        var timeToNextStation = ""
        var distanceToNextStation = ""
        var stayOnSameVehicle = ""
        var supportLineInfo = ""
        var numberOfStops = 0
        var stopNames = arrayOfNulls<String>(0)
        var routeInstructionsList = arrayOfNulls<RouteInstructionItem>(0)
        var isWalk: Boolean = false
        var isExpended: Boolean = false
    }

    internal class RouteItem {
        var segmentCounts = 0
        var tripTimeInterval = ""
        var tripDuration = ""
        var numberOfChanges = ""
        var walkingInfo = ""
        var frequency = ""
        var fare = ""
        var warning = ""
        var tripSegments = arrayOfNulls<RouteSegmentItem>(0)
    }

    private val iconSize =
        GEMApplication.appResources().getDimension(R.dimen.route_list_instr_icon_size).toInt()

    private fun setItems(m_route: Route) {
        Helper.fillRouteDescriptionItems(m_route, m_header, m_routeDescriptionItems)
    }

    private fun setHeader(m_route: Route) {
        GEMSdkCall.checkCurrentThread()

        val routeSegmentsList = m_route.getSegments() ?: ArrayList()
        val nSegmentsCount = routeSegmentsList.size
        var nWalkingTime = 0
        var nWalkingDistance = 0
        var nNonWalkingSegmentsCount = 0
        var departureTime = Time()
        var arrivalTime = Time()
        var bLookForPTSegment = true

        m_header.m_tripSegments.clear()

        if (m_route.toPTRoute()?.getPTFrequency() ?: 0 > 0) {
            val timeText = Utils.getTimeText(m_route.toPTRoute()?.getPTFrequency() ?: 0)
            val tmp = String.format("%s %s", timeText.first, timeText.second)
            m_header.m_frequency = String.format(getUIString(StringIds.eStrEveryXTime), tmp)
        }
        m_header.m_fare = m_route.toPTRoute()?.getPTFare() ?: ""

        for (nSegmentIndex in 0 until nSegmentsCount) {
            val routeSegment = routeSegmentsList[nSegmentIndex]

            val routeSegmentItem = TRouteSegmentItem()

            if (routeSegment.getSegmentType() == ERouteSegmentType.ESTPublicTransport) {
                ++nNonWalkingSegmentsCount

                val ptRouteSegment = routeSegment.toPTRouteSegment()
                var bgColor = TRgba()
                ptRouteSegment?.let {
                    routeSegmentItem.m_type = it.getTransitType()
                    routeSegmentItem.m_name = it.getShortName() ?: ""

                    routeSegmentItem.m_visible = routeSegment.isSignificant()

                    val lineColor = it.getLineColor()
                    if (lineColor != null) {
                        bgColor = lineColor
                    }
                    routeSegmentItem.m_backgroundColor = bgColor.value()
                    routeSegmentItem.m_foregroundColor = it.getLineTextColor()?.value() ?: 0
                }

                if ((bgColor.red() == 255) && (bgColor.green() == 255) && (bgColor.blue() == 255)) {
                    routeSegmentItem.m_backgroundColor = TRgba(228, 228, 228, 255).value()
                }

                routeSegmentItem.m_realTimeStatus =
                    ptRouteSegment?.getRealtimeStatus() ?: ERealtimeStatus.ERSNotAvailable
                routeSegmentItem.m_hasWheelChairSupport =
                    ptRouteSegment?.getHasWheelchairSupport() ?: false
                routeSegmentItem.m_hasBicycleSupport =
                    ptRouteSegment?.getHasBicycleSupport() ?: false
                routeSegmentItem.m_iconId = Helper.getIconId(routeSegmentItem.m_type)

                if (bLookForPTSegment) {
                    bLookForPTSegment = false

                    m_header.m_nLeftImageId = routeSegmentItem.m_iconId

                    val from = ptRouteSegment?.getLineFrom() ?: ""
                    if (from.isNotEmpty()) {
                        m_header.m_from = String.format("from %s", from)
                    }
                }
            } else {
                nWalkingTime += routeSegment.getTimeDistance()?.getTotalTime() ?: 0
                nWalkingDistance += routeSegment.getTimeDistance()?.getTotalDistance() ?: 0
                routeSegmentItem.m_type = ETransitType.ETTWalk
                routeSegmentItem.m_iconId = Helper.getIconId(routeSegmentItem.m_type)

                routeSegmentItem.m_visible = routeSegment.isSignificant()

                val timeText =
                    Utils.getTimeText(routeSegment.getTimeDistance()?.getTotalTime() ?: 0)
                routeSegmentItem.m_travelTimeValue = timeText.first
                routeSegmentItem.m_travelTimeUnit = timeText.second
            }

            if (nSegmentIndex == 0) {
                departureTime = routeSegment.toPTRouteSegment()?.getDepartureTime() ?: Time()
            }
            if (nSegmentIndex == (nSegmentsCount - 1)) {
                arrivalTime = routeSegment.toPTRouteSegment()?.getArrivalTime() ?: Time()
            }

            m_header.m_tripSegments.add(routeSegmentItem)
        }

        val travelTime = m_route.getTimeDistance()?.getTotalTime() ?: 0

        if (bLookForPTSegment) {
            nWalkingTime = travelTime
        }

        var walkingTime = ""
        var walkingDistance = ""

        if (nWalkingTime > 0) {
            val timeText = Utils.getTimeText(nWalkingTime)
            walkingTime = String.format("%s %s", timeText.first, timeText.second)
        }

        if (nWalkingDistance > 0) {
            val distText =
                Utils.getDistText(nWalkingDistance, CommonSettings().getUnitSystem(), true)
            walkingDistance = String.format("%s %s", distText.first, distText.second)
        }

        m_header.m_walkingTime = walkingTime
        m_header.m_walkingDistance = walkingDistance
        val walkingInfo = "$walkingDistance ($walkingTime)"
        m_header.m_walkingInfo =
            String.format(getUIString(StringIds.eStrDistanceTimeWalking), walkingInfo)

        val bUse24HourNotation = true
        var departureTimeUnit = ""
        var arrivalTimeUnit = ""
        var departureTimeHour = departureTime.getHour()
        val departureTimeMinute = departureTime.getMinute()
        var arrivalTimeHour = arrivalTime.getHour()
        val arrivalTimeMinute = arrivalTime.getMinute()

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

        var departureTimeStr = String.format("%d:%02d", departureTimeHour, departureTimeMinute)
        var arrivalTimeStr = String.format("%d:%02d", arrivalTimeHour, arrivalTimeMinute)

        if (departureTimeUnit.isNotEmpty()) {
            departureTimeStr += " "
            departureTimeStr += departureTimeUnit
        }

        if (arrivalTimeUnit.isNotEmpty()) {
            arrivalTimeStr += " "
            arrivalTimeStr += arrivalTimeUnit
        }

        m_header.m_tripTimeInterval =
            String.format("%s - %s", departureTimeStr, arrivalTimeStr)

        m_header.m_departureTimeStr = departureTimeStr
        m_header.m_arrivalTimeStr = arrivalTimeStr

        val timeText = Utils.getTimeText(travelTime, false, false)
        m_header.m_tripDuration = String.format("%s %s", timeText.first, timeText.second)

        m_header.m_departureTime = departureTime
        m_header.m_arrivalTime = arrivalTime

        if (nNonWalkingSegmentsCount > 1) {
            --nNonWalkingSegmentsCount
            if (nNonWalkingSegmentsCount == 1) {
                m_header.m_numberOfChanges =
                    String.format(getUIString(StringIds.eStrNoOfTransfer), nNonWalkingSegmentsCount)
            } else {
                m_header.m_numberOfChanges = String.format(
                    getUIString(StringIds.eStrNoOfTransfers),
                    nNonWalkingSegmentsCount
                )
            }
        }

        if (m_route.toPTRoute()?.getPTRespectsAllConditions() == false) {
            m_header.m_warning = getUIString(StringIds.eStrNotAllPreferencesMet)
        }
    }

    private fun internalLoadItems(route: Route) {
        GEMSdkCall.checkCurrentThread()

        setHeader(route)
        setItems(route)

        m_nItems = 0
        m_agencies.clear()
        if (m_routeDescriptionItems.size > 0) {
            m_nItems = m_routeDescriptionItems.size + 1 // header
            for (i in m_routeDescriptionItems.indices) {
                if (m_routeDescriptionItems[i].m_agencyName.isNotEmpty() &&
                    (
                        m_routeDescriptionItems[i].m_agencyURL.isNotEmpty() ||
                            m_routeDescriptionItems[i].m_agencyFareURL.isNotEmpty() ||
                            m_routeDescriptionItems[i].m_agencyPhone.isNotEmpty()
                        )
                ) {
                    var j = 0
                    while (j < m_agencies.size) {
                        if (m_agencies[j].m_name === m_routeDescriptionItems[i].m_agencyName) {
                            break
                        }
                        ++j
                    }
                    if (j == m_agencies.size) {
                        m_agencies.add(
                            TAgencyInfo(
                                m_routeDescriptionItems[i].m_agencyName,
                                m_routeDescriptionItems[i].m_agencyURL,
                                m_routeDescriptionItems[i].m_agencyFareURL,
                                m_routeDescriptionItems[i].m_agencyPhone
                            )
                        )
                    }
                }
            }
            if (m_agencies.size > 0) {
                m_nItems += 1 // "AGENCY INFO" item
            }
        }
    }

    fun loadItems(route: Route) {
        GEMSdkCall.checkCurrentThread()
        
        internalLoadItems(route)

        routeItem = RouteItem()

        title = getTitle()
        agencyText = getAgencyText()
        itemsCount = getItemsCount()

        val width = TImageWidth()
        separatorIcon = getSeparatorImage(width, iconSize)

        routeItem.tripTimeInterval = getTripTimeInterval()
        routeItem.tripDuration = getTripDuration()
        routeItem.numberOfChanges = getNumberOfChanges()
        routeItem.walkingInfo = getWalkingInfo()
        routeItem.frequency = getFrequency()
        routeItem.fare = getFare()
        routeItem.warning = getWarning()

        val segmentsCount = getRouteSegmentsCount()
        routeItem.segmentCounts = segmentsCount.coerceAtLeast(0)

        routeItem.tripSegments = arrayOfNulls(segmentsCount)

        for (segmentIndex in 0 until segmentsCount) {
            routeItem.tripSegments[segmentIndex] = RouteSegmentItem()

            var color = getRouteSegmentBackgroundColor(segmentIndex)
            routeItem.tripSegments[segmentIndex]?.backgroundColor = AppUtils.getColor(color)

            color = getRouteSegmentForegroundColor(segmentIndex)
            routeItem.tripSegments[segmentIndex]?.foregroundColor = AppUtils.getColor(color)

            routeItem.tripSegments[segmentIndex]?.name = getRouteSegmentName(segmentIndex)
            routeItem.tripSegments[segmentIndex]?.travelTimeValue =
                getRouteSegmentTravelTimeValue(segmentIndex)
            routeItem.tripSegments[segmentIndex]?.travelTimeUnit =
                getRouteSegmentTravelTimeUnit(segmentIndex)
            routeItem.tripSegments[segmentIndex]?.visible = isRouteSegmentVisible(segmentIndex)

            routeItem.tripSegments[segmentIndex]?.icon =
                getRouteSegmentImage(segmentIndex, width, iconSize)

            val index = segmentIndex + 1

            routeItem.tripSegments[segmentIndex]?.departureTime = getStationDepartureTime(index)
            routeItem.tripSegments[segmentIndex]?.arrivalTime = getStationArrivalTime(index)
            routeItem.tripSegments[segmentIndex]?.startStationName = getStartStationName(index)
            routeItem.tripSegments[segmentIndex]?.stopStationName = getStopStationName(index)
            routeItem.tripSegments[segmentIndex]?.stationTimeInfo = getStationTimeInfo(index)

            color = getStationTimeInfoColor(index)
            routeItem.tripSegments[segmentIndex]?.stationTimeInfoColor =
                AppUtils.getColor(color)

            routeItem.tripSegments[segmentIndex]?.stationPlatform = getStationPlatform(index)
            routeItem.tripSegments[segmentIndex]?.toBLineName = getToBLineName(index)
            routeItem.tripSegments[segmentIndex]?.timeToNextStation =
                getTimeToNextStation(index)
            routeItem.tripSegments[segmentIndex]?.distanceToNextStation =
                getDistanceToNextStation(index)
            routeItem.tripSegments[segmentIndex]?.stayOnSameVehicle =
                getStayOnSameVehicle(index)
            routeItem.tripSegments[segmentIndex]?.supportLineInfo = getSupportLineInfo(index)
            routeItem.tripSegments[segmentIndex]?.isWalk = isWalking(index)
            routeItem.tripSegments[segmentIndex]?.numberOfStops = getNumberOfStops(index)

            if (routeItem.tripSegments[segmentIndex]?.isWalk == false) {
                val nStops =
                    routeItem.tripSegments[segmentIndex]?.numberOfStops?.coerceAtLeast(0)!!
                routeItem.tripSegments[segmentIndex]?.stopNames = arrayOfNulls(nStops)

                for (stopIndex in 0 until nStops) {
                    routeItem.tripSegments[segmentIndex]?.stopNames!![stopIndex] =
                        getStopName(index, stopIndex)
                }
            } else {
                routeItem.tripSegments[segmentIndex]?.stopNames = arrayOfNulls(0)

                var routeInstructionsCount = getInstructionsListCount(index)
                routeInstructionsCount = routeInstructionsCount.coerceAtLeast(0)

                routeItem.tripSegments[segmentIndex]?.routeInstructionsList =
                    arrayOfNulls(routeInstructionsCount)
                for (routeInstructionIndex in 0 until routeInstructionsCount) {
                    val item = RouteInstructionItem()
                    item.icon =
                        getInstructionImage(index, routeInstructionIndex, iconSize, iconSize)
                    item.simpleText = getInstructionText(index, routeInstructionIndex)
                    item.detailText = getInstructionDescription(index, routeInstructionIndex)
                    item.simpleStatusText = getInstructionDistance(index, routeInstructionIndex)
                    item.detailStatusText =
                        getInstructionDistanceUnit(index, routeInstructionIndex)

                    routeItem.tripSegments[segmentIndex]?.routeInstructionsList!![routeInstructionIndex] =
                        item
                }
            }
        }
    }

    class TImageWidth(var width: Int = 0)

    fun getTitle(): String {
        return getUIString(StringIds.eStrRouteDetails)
    }

    fun getItemsCount(): Int {
        return m_nItems
    }

    fun getTripTimeInterval(): String {
        return m_header.m_tripTimeInterval
    }

    fun getTripDuration(): String {
        return m_header.m_tripDuration
    }

    fun getNumberOfChanges(): String {
        return m_header.m_numberOfChanges
    }

    fun getRouteSegmentsCount(): Int {
        return m_header.m_tripSegments.size
    }

    fun isRouteSegmentVisible(segmentIndex: Int): Boolean {
        return if (segmentIndex >= 0 && segmentIndex < m_header.m_tripSegments.size) {
            m_header.m_tripSegments[segmentIndex].m_visible
        } else false
    }

    fun getRouteSegmentImage(
        segmentIndex: Int,
        width: TImageWidth,
        height: Int
    ): Bitmap? {
        if ((segmentIndex >= 0) && (segmentIndex < m_header.m_tripSegments.size)) {
            val iconId = m_header.m_tripSegments[segmentIndex].m_iconId
            width.width = (height * Utils.getImageAspectRatio(iconId)).toInt()

            return Utils.getImageAsBitmap(iconId, width.width, height)
        }

        return null
    }

    fun getRouteSegmentName(segmentIndex: Int): String {
        if ((segmentIndex >= 0) && (segmentIndex < m_header.m_tripSegments.size)) {
            return m_header.m_tripSegments[segmentIndex].m_name
        }
        return ""
    }

    fun getRouteSegmentBackgroundColor(segmentIndex: Int): Int {
        if ((segmentIndex >= 0) && (segmentIndex < m_header.m_tripSegments.size)) {
            return m_header.m_tripSegments[segmentIndex].m_backgroundColor
        }
        return 0
    }

    fun getRouteSegmentForegroundColor(segmentIndex: Int): Int {
        if ((segmentIndex >= 0) && (segmentIndex < m_header.m_tripSegments.size)) {
            return m_header.m_tripSegments[segmentIndex].m_foregroundColor
        }
        return 0
    }

    fun getRouteSegmentTravelTimeValue(segmentIndex: Int): String {
        if ((segmentIndex >= 0) && (segmentIndex < m_header.m_tripSegments.size)) {
            return m_header.m_tripSegments[segmentIndex].m_travelTimeValue
        }
        return ""
    }

    fun getRouteSegmentTravelTimeUnit(segmentIndex: Int): String {
        if ((segmentIndex >= 0) && (segmentIndex < m_header.m_tripSegments.size)) {
            return m_header.m_tripSegments[segmentIndex].m_travelTimeUnit
        }
        return ""
    }

    fun getSeparatorImage(width: TImageWidth, height: Int): Bitmap? {
        val iconId = GemIcons.PublicTransport.PublicTransport_Arrow.value
        width.width = (height * Utils.getImageAspectRatio(iconId)).toInt()

        return Utils.getImageAsBitmap(
            GemIcons.PublicTransport.PublicTransport_Arrow.value,
            width.width,
            height
        )
    }

    fun getWalkingInfo(): String {
        return m_header.m_walkingInfo
    }

    fun getFrequency(): String {
        return m_header.m_frequency
    }

    fun getFare(): String {
        return m_header.m_fare
    }

    fun getWarning(): String {
        return m_header.m_warning
    }

    fun getStartStationName(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_startStationName
        }
        return tmp
    }

    fun getStopStationName(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_stopStationName
        }
        return tmp
    }

    fun getStationTimeInfo(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = if (m_routeDescriptionItems[itemIndex].m_stationEarlyTime.isNotEmpty()) {
                m_routeDescriptionItems[itemIndex].m_stationEarlyTime
            } else {
                m_routeDescriptionItems[itemIndex].m_stationLaterTime
            }
        }
        return tmp
    }

    fun getStationTimeInfoColor(index: Int): Int {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            if (m_routeDescriptionItems[itemIndex].m_stationEarlyTime.isNotEmpty()) {
                return TRgba(58, 145, 86, 255).value()
            }
            if (m_routeDescriptionItems[itemIndex].m_stationLaterTime.isEmpty()) {
                return TRgba(242, 46, 59, 255).value()
            }
        }
        return TRgba(0, 0, 0, 255).value()
    }

    fun getStationPlatform(index: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            return m_routeDescriptionItems[itemIndex].m_stationPlatform
        }
        return ""
    }

    fun getStationDepartureTime(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_departureTime
        }
        return tmp
    }

    fun getStationArrivalTime(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            val crtItemIsWalk = m_routeDescriptionItems[itemIndex].m_isWalk
            val nextItemIsWalk =
                (itemIndex < (m_routeDescriptionItems.size - 1)) && m_routeDescriptionItems[itemIndex + 1].m_isWalk
            if ((!crtItemIsWalk && !nextItemIsWalk) || (itemIndex == (m_routeDescriptionItems.size - 1))) {
                tmp = m_routeDescriptionItems[itemIndex].m_arrivalTime
            }
        }
        return tmp
    }

    fun getToBLineName(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_toBLineName
        }
        return tmp
    }

    fun getSupportLineInfo(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_supportLineInfo
        }
        return tmp
    }

    fun isWalking(index: Int): Boolean {
        val itemIndex = index - 1
        return if (itemIndex >= 0 && itemIndex < m_routeDescriptionItems.size) {
            m_routeDescriptionItems[itemIndex].m_isWalk
        } else false
    }

    fun getNumberOfStops(index: Int): Int {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            return m_routeDescriptionItems[itemIndex].m_stopNames.size
        }
        return 0
    }

    fun getStopName(index: Int, stopIndex: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_stopNames[stopIndex]
        }
        return tmp
    }

    fun getTimeToNextStation(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_timeToNextStation
        }
        return tmp
    }

    fun getDistanceToNextStation(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_distanceToNextStation
        }
        return tmp
    }

    fun getStayOnSameVehicle(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            tmp = m_routeDescriptionItems[itemIndex].m_stayOnSameVehicle
        }
        return tmp
    }

    fun getInstructionsListCount(index: Int): Int {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            return m_routeDescriptionItems[itemIndex].m_instructionsList.size
        }
        return 0
    }

    fun getInstructionText(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < m_routeDescriptionItems[itemIndex].m_instructionsList.size)) {
                return m_routeDescriptionItems[itemIndex].m_instructionsList[routeInstructionIndex].text
            }
        }
        return ""
    }

    fun getInstructionDescription(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < m_routeDescriptionItems[itemIndex].m_instructionsList.size)) {
                return m_routeDescriptionItems[itemIndex].m_instructionsList[routeInstructionIndex].description
            }
        }
        return ""
    }

    fun getInstructionDistance(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < m_routeDescriptionItems[itemIndex].m_instructionsList.size)) {
                return m_routeDescriptionItems[itemIndex].m_instructionsList[routeInstructionIndex].statusText
            }
        }
        return ""
    }

    fun getInstructionDistanceUnit(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < m_routeDescriptionItems[itemIndex].m_instructionsList.size)) {
                return m_routeDescriptionItems[itemIndex].m_instructionsList[routeInstructionIndex].statusDescription
            }
        }
        return ""
    }

    fun getInstructionImage(
        index: Int,
        routeInstructionIndex: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < m_routeDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < m_routeDescriptionItems[itemIndex].m_instructionsList.size)) {
                val instr =
                    m_routeDescriptionItems[itemIndex].m_instructionsList[routeInstructionIndex]
                return instr.getBitmap(width, height)
            }
        }
        return null
    }

    fun getAgenciesCount(): Int {
        return m_agencies.size
    }

    fun getAgencyText(): String {
        var tmp = ""
        if (m_agencies.size > 0) {
            tmp = getUIString(StringIds.eStrAgencyInfo)
            tmp.toUpperCase(Locale.getDefault())
        }
        return tmp
    }

    fun getAgencyName(index: Int): String {
        if (index >= 0 && index < m_agencies.size) {
            return m_agencies[index].m_name
        }
        return ""
    }

    fun getAgencyURL(index: Int): String {
        if (index >= 0 && index < m_agencies.size) {
            return m_agencies[index].m_url
        }
        return ""
    }

    fun getAgencyFareURL(index: Int): String {
        if (index >= 0 && index < m_agencies.size) {
            return m_agencies[index].m_fare_url
        }
        return ""
    }

    fun getAgencyPhone(index: Int): String {
        if (index >= 0 && index < m_agencies.size) {
            return m_agencies[index].m_phone
        }
        return ""
    }

    fun getAgencyURLImage(): Image? {
        return ImageDatabase().getImageById(GemIcons.Other_UI.LocationDetails_OpenWebsite.value)
    }

    fun getAgencyFareImage(): Image? {
        return ImageDatabase().getImageById(GemIcons.Other_UI.LocationDetails_BuyTickets.value)
    }

    fun getAgencyPhoneImage(): Image? {
        return ImageDatabase().getImageById(GemIcons.Other_UI.LocationDetails_PhoneCall.value)
    }
}
