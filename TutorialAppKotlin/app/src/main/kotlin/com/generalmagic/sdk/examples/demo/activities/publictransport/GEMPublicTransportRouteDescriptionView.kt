/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.publictransport

import android.graphics.Bitmap
import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.AppUtils
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.routesandnavigation.ERealtimeStatus
import com.generalmagic.sdk.routesandnavigation.ETransitType
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.SdkUtil.getDistText
import com.generalmagic.sdk.util.SdkUtil.getTimeText
import com.generalmagic.sdk.util.SdkUtil.getUIString
import com.generalmagic.sdk.util.StringIds
import java.util.*
import kotlin.collections.ArrayList

object GEMPublicTransportRouteDescriptionView {

    internal lateinit var routeItem: RouteItem
    internal var separatorIcon: Bitmap? = null
    internal lateinit var title: String
    internal lateinit var agencyText: String
    internal var itemsCount = -1

    private var mNItems = 0
    private var mHeader = TRouteItem()
    private var mAgencies = ArrayList<TAgencyInfo>()
    var mRouteDescriptionItems = ArrayList<TRouteDescriptionItem>()

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

    private fun setItems(mRoute: Route) {
        Helper.fillRouteDescriptionItems(mRoute, mHeader, mRouteDescriptionItems)
    }

    private fun setHeader(mRoute: Route) {
        SdkCall.checkCurrentThread()

        val routeSegmentsList = mRoute.getSegments() ?: ArrayList()
        val nSegmentsCount = routeSegmentsList.size
        var nWalkingTime = 0
        var nWalkingDistance = 0
        var nNonWalkingSegmentsCount = 0
        var departureTime = Time()
        var arrivalTime = Time()
        var bLookForPTSegment = true

        mHeader.mTripSegments.clear()

        if (mRoute.toPTRoute()?.getPTFrequency() ?: 0 > 0) {
            val timeText = getTimeText(mRoute.toPTRoute()?.getPTFrequency() ?: 0)
            val tmp = String.format("%s %s", timeText.first, timeText.second)
            mHeader.mFrequency = String.format(getUIString(StringIds.eStrEveryXTime), tmp)
        }
        mHeader.mFare = mRoute.toPTRoute()?.getPTFare() ?: ""

        for (nSegmentIndex in 0 until nSegmentsCount) {
            val routeSegment = routeSegmentsList[nSegmentIndex]

            val routeSegmentItem = TRouteSegmentItem()

            if (routeSegment.isPublicTransportSegment()) {
                ++nNonWalkingSegmentsCount

                val ptRouteSegment = routeSegment.toPTRouteSegment()
                var bgColor = Rgba()
                ptRouteSegment?.let {
                    routeSegmentItem.mType = it.getTransitType()
                    routeSegmentItem.mName = it.getShortName() ?: ""

                    routeSegmentItem.mVisible = ptRouteSegment.isSignificant()

                    val lineColor = it.getLineColor()
                    if (lineColor != null) {
                        bgColor = lineColor
                    }
                    routeSegmentItem.mBackgroundColor = bgColor.value()
                    routeSegmentItem.mForegroundColor = it.getLineTextColor()?.value() ?: 0
                }

                if ((bgColor.red() == 255) && (bgColor.green() == 255) && (bgColor.blue() == 255)) {
                    routeSegmentItem.mBackgroundColor = Rgba(228, 228, 228, 255).value()
                }

                routeSegmentItem.mRealTimeStatus =
                    ptRouteSegment?.getRealtimeStatus() ?: ERealtimeStatus.NotAvailable
                routeSegmentItem.mHasWheelChairSupport =
                    ptRouteSegment?.getHasWheelchairSupport() ?: false
                routeSegmentItem.mHasBicycleSupport =
                    ptRouteSegment?.getHasBicycleSupport() ?: false
                routeSegmentItem.mIconId = Helper.getIconId(routeSegmentItem.mType)

                if (bLookForPTSegment) {
                    bLookForPTSegment = false

                    mHeader.mNLeftImageId = routeSegmentItem.mIconId

                    val from = ptRouteSegment?.getLineFrom() ?: ""
                    if (from.isNotEmpty()) {
                        mHeader.mFrom = String.format("from %s", from)
                    }
                }
            } else {
                nWalkingTime += routeSegment.getTimeDistance()?.getTotalTime() ?: 0
                nWalkingDistance += routeSegment.getTimeDistance()?.getTotalDistance() ?: 0
                routeSegmentItem.mType = ETransitType.Walk
                routeSegmentItem.mIconId = Helper.getIconId(routeSegmentItem.mType)

                routeSegmentItem.mVisible = true//routeSegment.isSignificant()

                val timeText =
                    getTimeText(routeSegment.getTimeDistance()?.getTotalTime() ?: 0)
                routeSegmentItem.mTravelTimeValue = timeText.first
                routeSegmentItem.mTravelTimeUnit = timeText.second
            }

            if (nSegmentIndex == 0) {
                departureTime = routeSegment.toPTRouteSegment()?.getDepartureTime() ?: Time()
            }
            if (nSegmentIndex == (nSegmentsCount - 1)) {
                arrivalTime = routeSegment.toPTRouteSegment()?.getArrivalTime() ?: Time()
            }

            mHeader.mTripSegments.add(routeSegmentItem)
        }

        val travelTime = mRoute.getTimeDistance()?.getTotalTime() ?: 0

        if (bLookForPTSegment) {
            nWalkingTime = travelTime
        }

        var walkingTime = ""
        var walkingDistance = ""

        if (nWalkingTime > 0) {
            val timeText = getTimeText(nWalkingTime)
            walkingTime = String.format("%s %s", timeText.first, timeText.second)
        }

        if (nWalkingDistance > 0) {
            val distText =
                getDistText(nWalkingDistance, SdkSettings().getUnitSystem(), true)
            walkingDistance = String.format("%s %s", distText.first, distText.second)
        }

        mHeader.mWalkingTime = walkingTime
        mHeader.mWalkingDistance = walkingDistance
        val walkingInfo = "$walkingDistance ($walkingTime)"
        mHeader.mWalkingInfo =
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

        mHeader.mTripTimeInterval =
            String.format("%s - %s", departureTimeStr, arrivalTimeStr)

        mHeader.mDepartureTimeStr = departureTimeStr
        mHeader.mArrivalTimeStr = arrivalTimeStr

        val timeText = getTimeText(travelTime, bForceHours = false, bCapitalizeResult = false)
        mHeader.mTripDuration = String.format("%s %s", timeText.first, timeText.second)

        mHeader.mDepartureTime = departureTime
        mHeader.mArrivalTime = arrivalTime

        if (nNonWalkingSegmentsCount > 1) {
            --nNonWalkingSegmentsCount
            if (nNonWalkingSegmentsCount == 1) {
                mHeader.mNumberOfChanges =
                    String.format(getUIString(StringIds.eStrNoOfTransfer), nNonWalkingSegmentsCount)
            } else {
                mHeader.mNumberOfChanges = String.format(
                    getUIString(StringIds.eStrNoOfTransfers),
                    nNonWalkingSegmentsCount
                )
            }
        }

        if (mRoute.toPTRoute()?.getPTRespectsAllConditions() == false) {
            mHeader.mWarning = getUIString(StringIds.eStrNotAllPreferencesMet)
        }
    }

    private fun internalLoadItems(route: Route) {
        SdkCall.checkCurrentThread()

        setHeader(route)
        setItems(route)

        mNItems = 0
        mAgencies.clear()
        if (mRouteDescriptionItems.size > 0) {
            mNItems = mRouteDescriptionItems.size + 1 // header
            for (i in mRouteDescriptionItems.indices) {
                if (mRouteDescriptionItems[i].mAgencyName.isNotEmpty() &&
                    (
                        mRouteDescriptionItems[i].mAgencyURL.isNotEmpty() ||
                            mRouteDescriptionItems[i].mAgencyFareURL.isNotEmpty() ||
                            mRouteDescriptionItems[i].mAgencyPhone.isNotEmpty()
                        )
                ) {
                    var j = 0
                    while (j < mAgencies.size) {
                        if (mAgencies[j].mName === mRouteDescriptionItems[i].mAgencyName) {
                            break
                        }
                        ++j
                    }
                    if (j == mAgencies.size) {
                        mAgencies.add(
                            TAgencyInfo(
                                mRouteDescriptionItems[i].mAgencyName,
                                mRouteDescriptionItems[i].mAgencyURL,
                                mRouteDescriptionItems[i].mAgencyFareURL,
                                mRouteDescriptionItems[i].mAgencyPhone
                            )
                        )
                    }
                }
            }
            if (mAgencies.size > 0) {
                mNItems += 1 // "AGENCY INFO" item
            }
        }
    }

    fun loadItems(route: Route) {
        SdkCall.checkCurrentThread()

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

    private fun getTitle(): String {
        return getUIString(StringIds.eStrRouteDetails)
    }

    private fun getItemsCount(): Int {
        return mNItems
    }

    private fun getTripTimeInterval(): String {
        return mHeader.mTripTimeInterval
    }

    private fun getTripDuration(): String {
        return mHeader.mTripDuration
    }

    private fun getNumberOfChanges(): String {
        return mHeader.mNumberOfChanges
    }

    private fun getRouteSegmentsCount(): Int {
        return mHeader.mTripSegments.size
    }

    private fun isRouteSegmentVisible(segmentIndex: Int): Boolean {
        return if (segmentIndex >= 0 && segmentIndex < mHeader.mTripSegments.size) {
            mHeader.mTripSegments[segmentIndex].mVisible
        } else false
    }

    @Suppress("SameParameterValue")
    private fun getRouteSegmentImage(
        segmentIndex: Int,
        width: TImageWidth,
        height: Int
    ): Bitmap? {
        if ((segmentIndex >= 0) && (segmentIndex < mHeader.mTripSegments.size)) {
            val iconId = mHeader.mTripSegments[segmentIndex].mIconId
            width.width = (height * Utils.getImageAspectRatio(iconId)).toInt()

            return Utils.getImageAsBitmap(iconId, width.width, height)
        }

        return null
    }

    private fun getRouteSegmentName(segmentIndex: Int): String {
        if ((segmentIndex >= 0) && (segmentIndex < mHeader.mTripSegments.size)) {
            return mHeader.mTripSegments[segmentIndex].mName
        }
        return ""
    }

    private fun getRouteSegmentBackgroundColor(segmentIndex: Int): Int {
        if ((segmentIndex >= 0) && (segmentIndex < mHeader.mTripSegments.size)) {
            return mHeader.mTripSegments[segmentIndex].mBackgroundColor
        }
        return 0
    }

    private fun getRouteSegmentForegroundColor(segmentIndex: Int): Int {
        if ((segmentIndex >= 0) && (segmentIndex < mHeader.mTripSegments.size)) {
            return mHeader.mTripSegments[segmentIndex].mForegroundColor
        }
        return 0
    }

    private fun getRouteSegmentTravelTimeValue(segmentIndex: Int): String {
        if ((segmentIndex >= 0) && (segmentIndex < mHeader.mTripSegments.size)) {
            return mHeader.mTripSegments[segmentIndex].mTravelTimeValue
        }
        return ""
    }

    private fun getRouteSegmentTravelTimeUnit(segmentIndex: Int): String {
        if ((segmentIndex >= 0) && (segmentIndex < mHeader.mTripSegments.size)) {
            return mHeader.mTripSegments[segmentIndex].mTravelTimeUnit
        }
        return ""
    }

    @Suppress("SameParameterValue")
    private fun getSeparatorImage(width: TImageWidth, height: Int): Bitmap? {
        val iconId = SdkIcons.PublicTransport.PublicTransport_Arrow.value
        width.width = (height * Utils.getImageAspectRatio(iconId)).toInt()

        return Utils.getImageAsBitmap(
            SdkIcons.PublicTransport.PublicTransport_Arrow.value,
            width.width,
            height
        )
    }

    private fun getWalkingInfo(): String {
        return mHeader.mWalkingInfo
    }

    private fun getFrequency(): String {
        return mHeader.mFrequency
    }

    private fun getFare(): String {
        return mHeader.mFare
    }

    private fun getWarning(): String {
        return mHeader.mWarning
    }

    private fun getStartStationName(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mStartStationName
        }
        return tmp
    }

    private fun getStopStationName(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mStopStationName
        }
        return tmp
    }

    private fun getStationTimeInfo(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = if (mRouteDescriptionItems[itemIndex].mStationEarlyTime.isNotEmpty()) {
                mRouteDescriptionItems[itemIndex].mStationEarlyTime
            } else {
                mRouteDescriptionItems[itemIndex].mStationLaterTime
            }
        }
        return tmp
    }

    private fun getStationTimeInfoColor(index: Int): Int {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            if (mRouteDescriptionItems[itemIndex].mStationEarlyTime.isNotEmpty()) {
                return Rgba(58, 145, 86, 255).value()
            }
            if (mRouteDescriptionItems[itemIndex].mStationLaterTime.isEmpty()) {
                return Rgba(242, 46, 59, 255).value()
            }
        }
        return Rgba(0, 0, 0, 255).value()
    }

    private fun getStationPlatform(index: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            return mRouteDescriptionItems[itemIndex].mStationPlatform
        }
        return ""
    }

    private fun getStationDepartureTime(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mDepartureTime
        }
        return tmp
    }

    private fun getStationArrivalTime(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            val crtItemIsWalk = mRouteDescriptionItems[itemIndex].mIsWalk
            val nextItemIsWalk =
                (itemIndex < (mRouteDescriptionItems.size - 1)) && mRouteDescriptionItems[itemIndex + 1].mIsWalk
            if ((!crtItemIsWalk && !nextItemIsWalk) || (itemIndex == (mRouteDescriptionItems.size - 1))) {
                tmp = mRouteDescriptionItems[itemIndex].mArrivalTime
            }
        }
        return tmp
    }

    private fun getToBLineName(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mToBLineName
        }
        return tmp
    }

    private fun getSupportLineInfo(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mSupportLineInfo
        }
        return tmp
    }

    private fun isWalking(index: Int): Boolean {
        val itemIndex = index - 1
        return if (itemIndex >= 0 && itemIndex < mRouteDescriptionItems.size) {
            mRouteDescriptionItems[itemIndex].mIsWalk
        } else false
    }

    private fun getNumberOfStops(index: Int): Int {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            return mRouteDescriptionItems[itemIndex].mStopNames.size
        }
        return 0
    }

    private fun getStopName(index: Int, stopIndex: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mStopNames[stopIndex]
        }
        return tmp
    }

    private fun getTimeToNextStation(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mTimeToNextStation
        }
        return tmp
    }

    private fun getDistanceToNextStation(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mDistanceToNextStation
        }
        return tmp
    }

    private fun getStayOnSameVehicle(index: Int): String {
        var tmp = ""
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            tmp = mRouteDescriptionItems[itemIndex].mStayOnSameVehicle
        }
        return tmp
    }

    private fun getInstructionsListCount(index: Int): Int {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            return mRouteDescriptionItems[itemIndex].mInstructionsList.size
        }
        return 0
    }

    private fun getInstructionText(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < mRouteDescriptionItems[itemIndex].mInstructionsList.size)) {
                return mRouteDescriptionItems[itemIndex].mInstructionsList[routeInstructionIndex].text
            }
        }
        return ""
    }

    private fun getInstructionDescription(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < mRouteDescriptionItems[itemIndex].mInstructionsList.size)) {
                return mRouteDescriptionItems[itemIndex].mInstructionsList[routeInstructionIndex].description
            }
        }
        return ""
    }

    private fun getInstructionDistance(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < mRouteDescriptionItems[itemIndex].mInstructionsList.size)) {
                return mRouteDescriptionItems[itemIndex].mInstructionsList[routeInstructionIndex].statusText
            }
        }
        return ""
    }

    private fun getInstructionDistanceUnit(index: Int, routeInstructionIndex: Int): String {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < mRouteDescriptionItems[itemIndex].mInstructionsList.size)) {
                return mRouteDescriptionItems[itemIndex].mInstructionsList[routeInstructionIndex].statusDescription
            }
        }
        return ""
    }

    @Suppress("SameParameterValue")
    private fun getInstructionImage(
        index: Int,
        routeInstructionIndex: Int,
        width: Int,
        height: Int
    ): Bitmap? {
        val itemIndex = index - 1
        if ((itemIndex >= 0) && (itemIndex < mRouteDescriptionItems.size)) {
            if ((routeInstructionIndex >= 0) && (routeInstructionIndex < mRouteDescriptionItems[itemIndex].mInstructionsList.size)) {
                val instr =
                    mRouteDescriptionItems[itemIndex].mInstructionsList[routeInstructionIndex]
                return instr.getBitmap(width, height)
            }
        }
        return null
    }

    fun getAgenciesCount(): Int {
        return mAgencies.size
    }

    fun getAgencyText(): String {
        var tmp = ""
        if (mAgencies.size > 0) {
            tmp = getUIString(StringIds.eStrAgencyInfo)
            tmp.uppercase(Locale.getDefault())
        }
        return tmp
    }

    fun getAgencyName(index: Int): String {
        if (index >= 0 && index < mAgencies.size) {
            return mAgencies[index].mName
        }
        return ""
    }

    fun getAgencyURL(index: Int): String {
        if (index >= 0 && index < mAgencies.size) {
            return mAgencies[index].mUrl
        }
        return ""
    }

    fun getAgencyFareURL(index: Int): String {
        if (index >= 0 && index < mAgencies.size) {
            return mAgencies[index].mFare_url
        }
        return ""
    }

    fun getAgencyPhone(index: Int): String {
        if (index >= 0 && index < mAgencies.size) {
            return mAgencies[index].mPhone
        }
        return ""
    }

    fun getAgencyURLImage(): Image? {
        return ImageDatabase().getImageById(SdkIcons.Other_UI.LocationDetails_OpenWebsite.value)
    }

    fun getAgencyFareImage(): Image? {
        return ImageDatabase().getImageById(SdkIcons.Other_UI.LocationDetails_BuyTickets.value)
    }

    fun getAgencyPhoneImage(): Image? {
        return ImageDatabase().getImageById(SdkIcons.Other_UI.LocationDetails_PhoneCall.value)
    }
}
