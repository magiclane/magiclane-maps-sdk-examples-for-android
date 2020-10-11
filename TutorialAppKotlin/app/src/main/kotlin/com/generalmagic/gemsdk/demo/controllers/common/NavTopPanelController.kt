/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.controllers.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.util.StaticsHolder
import com.generalmagic.gemsdk.demo.util.Util
import com.generalmagic.gemsdk.demo.util.Util.Companion.setPanelBackground
import com.generalmagic.gemsdk.demo.util.UtilUITexts
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getDistText
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getTimeText
import com.generalmagic.gemsdk.demo.util.Utils.Companion.getUIString
import com.generalmagic.gemsdk.extensions.StringIds
import com.generalmagic.gemsdk.models.Marker
import com.generalmagic.gemsdk.models.RouteTrafficEvent
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.GemIcons
import kotlinx.android.synthetic.main.nav_top_panel.view.*

class NavTopPanelController(context: Context, attrs: AttributeSet?) :
    RelativeLayout(context, attrs) {

    private val navInfoPanelFactor = 0.45

    private val turnImageSize =
        context?.resources?.getDimension(R.dimen.nav_top_panel_big_img_size)?.toInt() ?: 0

    private val navigationPanelPadding =
        context?.resources?.getDimension(R.dimen.nav_top_panel_padding)?.toInt() ?: 0

    private val signPostImageSize =
        context?.resources?.getDimension(R.dimen.signPostImageSize)?.toInt() ?: 0

    val navigationImageSize =
        context?.resources?.getDimension(R.dimen.navigationImageSize)?.toInt() ?: 0

    private var endOfSectionImage = GEMSdkCall.execute {
        getTrafficEndOfSectionIcon(navigationImageSize, navigationImageSize)
    }

    private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)

    // ----------------------------------------------------------------------------------------------

    fun update(navInstr: NavigationInstruction?, route: Route?, alarmService: AlarmService?) {
        reset()

        GEMSdkCall.execute {
            this.navInstr = navInstr
            this.route = route
            this.alarmService = alarmService
            this.trafficEvent = pickTrafficEvent(navInstr, route)

            updateNavigationInfo(navInstr)
            updateTrafficEvent(trafficEvent)
            updateAlarmsInfo(alarmService)
        }

        updateNavigationTopPanel()
        updateTrafficPanel()
        updateAlarmPanel()
    }

    fun update(instruction: RouteInstruction?) {
        reset()

        GEMSdkCall.execute {
            updateNavigationInfo(instruction)
        }

        updateNavigationTopPanel()
    }

    fun update(event: RouteTrafficEvent, routeLength: Int) {
        reset()

        GEMSdkCall.execute {
            this.trafficEvent = event
            updateNavigationInfo(trafficEvent, routeLength)
        }

        updateNavigationTopPanel()
    }

    // ----------------------------------------------------------------------------------------------

    private fun updateNavigationInfo(navInstr: NavigationInstruction?) {
        GEMSdkCall.checkCurrentThread()

        if (navInstr == null) return

        if (navInstr.getNavigationStatus() != GEMError.KNoError.value) {
            return
        }

        val bHasNextRoadCode = (navInstr.getNextRoadInformation()?.size ?: 0) > 0
        val turnDetails = navInstr.getNextTurnDetails()

        if (turnDetails != null &&
            (turnDetails.getInfoType() == TTurnInfoType.ETI_WaypointEvent) &&
            (
                (turnDetails.getEvent() == TTurnWaypointType.ETW_Stop.value) ||
                    (turnDetails.getEvent() == TTurnWaypointType.ETW_Intermediate.value)
                )
        ) {
            turnInstruction = navInstr.getNextTurnInstruction() ?: ""
        } else if (navInstr.hasSignpostInfo()) {
            turnInstruction = navInstr.getSignpostInstruction() ?: ""
            if (!turnInstruction.isNullOrEmpty()) {
                turnInstruction = navInstr.getNextStreetName() ?: ""
            }
        } else {
            turnInstruction = navInstr.getNextStreetName() ?: ""
        }

        if (!turnInstruction.isNullOrEmpty() && !bHasNextRoadCode) {
            turnInstruction = navInstr.getNextTurnInstruction() ?: ""
        }

        val distanceToNextTurnTexts = getDistText(
            navInstr.getTimeDistanceToNextTurn()?.getTotalDistance() ?: 0,
            CommonSettings().getUnitSystem(),
            true
        )

        // sizes
        val screen = StaticsHolder.gemMapScreen()
        val surfaceWidth = screen?.getViewPort()?.width() ?: 0
        val surfaceHeight = screen?.getViewPort()?.height() ?: 0

        val panelWidth = if (surfaceWidth <= surfaceHeight) {
            surfaceWidth
        } else {
            (surfaceWidth * navInfoPanelFactor).toInt()
        }

        val availableWidthForMiddlePanel =
            panelWidth - turnImageSize - 3 * navigationPanelPadding

        // actual data update
        distanceToNextTurn = distanceToNextTurnTexts.first
        distanceToNextTurnUnit = distanceToNextTurnTexts.second
        turnImage = getNextTurnImage(navInstr, turnImageSize, turnImageSize)
        signPostImage = getSignpostImage(
            navInstr,
            availableWidthForMiddlePanel,
            signPostImageSize
        ).second

        roadCodeImage = if (signPostImage == null) {
            getRoadCodeImage(navInstr, availableWidthForMiddlePanel, navigationImageSize).second
        } else {
            null
        }
    }

    private fun updateNavigationInfo(event: RouteTrafficEvent?, routeLength: Int) {
        GEMSdkCall.checkCurrentThread()

        updateTrafficEvent(event)

        val remainingDistance = routeLength - (event?.getDistanceToDestination() ?: 0)

        val distFromStartTexts = getDistText(
            remainingDistance,
            CommonSettings().getUnitSystem(),
            true
        )

        val text = String.format(
            "%s\n%s",
            UtilUITexts.formatTrafficDelayAndLength(event),
            event?.getDescription() ?: ""
        )

        turnImage = trafficImage
        turnInstruction = text
        distanceToNextTurn = distFromStartTexts.first
        distanceToNextTurnUnit = distFromStartTexts.second
    }

    private fun updateNavigationInfo(event: RouteInstruction?) {
        GEMSdkCall.checkCurrentThread()

        val distanceToNextTurnTexts = getDistText(
            event?.getTraveledTimeDistance()?.getTotalDistance() ?: 0,
            CommonSettings().getUnitSystem(),
            true
        )

        turnImage = getTurnImage(event, turnImageSize, turnImageSize)
        turnInstruction = event?.getTurnInstruction() ?: ""
        distanceToNextTurn = distanceToNextTurnTexts.first
        distanceToNextTurnUnit = distanceToNextTurnTexts.second
    }

    // ----------------------------------------------------------------------------------------------

    private fun updateTrafficEvent(trafficEvent: RouteTrafficEvent?) {
        if (trafficEvent == null) return

        trafficEventDescription = trafficEvent.getDescription() ?: ""

        val distance = if (bInsideTrafficEvent) {
            nRemainingDistanceInsideTrafficEvent
        } else {
            nDistanceToTrafficEvent
        }

        val distanceToTrafficPair = getDistText(
            distance,
            CommonSettings().getUnitSystem(),
            true
        )

        distanceToTraffic = distanceToTrafficPair.first
        distanceToTrafficUnit = distanceToTrafficPair.second

        val theFormat = if (bInsideTrafficEvent) {
            getUIString(StringIds.eStrOutIn)
        } else {
            getUIString(StringIds.eStrIn)
        }

        distanceToTrafficPrefix = String.format(theFormat, "").trim()

        if (!trafficEvent.isRoadblock()) {
            if (bInsideTrafficEvent) {
                if (trafficEvent.getLength() > 0) {
                    val nRemainingTimeInsideTrafficEvent = (
                        trafficEvent.getDelay() *
                            nRemainingDistanceInsideTrafficEvent
                        ) / trafficEvent.getLength()

                    val trafficDelayTextPair = getTimeText(nRemainingTimeInsideTrafficEvent)

                    trafficDelayTime = trafficDelayTextPair.first
                    trafficDelayTimeUnit = trafficDelayTextPair.second
                }
            } else {
                val trafficDistTextPair = getDistText(
                    trafficEvent.getLength(),
                    CommonSettings().getUnitSystem(),
                    true
                )

                trafficDelayDistance = trafficDistTextPair.first
                trafficDelayDistanceUnit = trafficDistTextPair.second

                val trafficDelayTextPair = getTimeText(trafficEvent.getDelay())

                trafficDelayTime = String.format("+%s", trafficDelayTextPair.first)
                trafficDelayTimeUnit = trafficDelayTextPair.second
            }
        }

        trafficImage = GEMSdkCall.execute {
            getTrafficImage(trafficEvent, navigationImageSize, navigationImageSize)
        }
    }

    private fun pickTrafficEvent(
        navInstr: NavigationInstruction?,
        route: Route?
    ): RouteTrafficEvent? {
        GEMSdkCall.checkCurrentThread()

        if (navInstr == null || route == null) return null

        if (navInstr.getNavigationStatus() != GEMError.KNoError.value) return null
        val trafficEventsList = route.getTrafficEvents() ?: return null

        val remainingTravelDistance =
            navInstr.getRemainingTravelTimeDistance()?.getTotalDistance() ?: 0

        // pick current traffic event
        for (event in trafficEventsList) {
            val distToDest = event.getDistanceToDestination()
            nDistanceToTrafficEvent =
                remainingTravelDistance - distToDest

            if (nDistanceToTrafficEvent <= 0) {
                nRemainingDistanceInsideTrafficEvent =
                    event.getLength() - (distToDest - remainingTravelDistance)

                if (nRemainingDistanceInsideTrafficEvent >= 0) {
                    bInsideTrafficEvent = true
                }
            }

            if (nDistanceToTrafficEvent >= 0 ||
                nRemainingDistanceInsideTrafficEvent >= 0
            ) {
                return event
            }
        }

        return null
    }

    private fun updateAlarmsInfo(alarmService: AlarmService?) {
        GEMSdkCall.checkCurrentThread()
        alarmService ?: return

        val maxDistanceToAlarm = alarmService.getLandmarkAlarmDistance()

        val markersList = alarmService.getMarkerAlarms()
        if (markersList == null || markersList.size() == 0) return

        val distance = markersList.getDistance(0)
        if (distance < maxDistanceToAlarm) {
            val textsPair = getDistText(distance.toInt(), CommonSettings().getUnitSystem(), true)

            distanceToSafetyCameraAlarm = textsPair.first
            distanceToSafetyCameraAlarmUnit = textsPair.second

            currentAlarmedMarker = markersList.getItem(0)
        }
    }

    // ----------------------------------------------------------------------------------------------

    private fun updateNavigationTopPanel() {
        this.visibility = View.VISIBLE
        var bDisplayRoadCode = true
        var bDisplayRouteInstruction = true
        var bDisplayedRoadCode = false

        statusText?.let {
            status_text.visibility = View.VISIBLE
            status_text.text = it
        } ?: run { status_text.visibility = View.GONE }

        turn_image.setImageBitmap(turnImage)
        turn_distance.text = distanceToNextTurn
        turn_distance_unit.text = distanceToNextTurnUnit

        signPostImage?.let { itSignPostImage ->
            sign_post.visibility = View.VISIBLE
            sign_post.setImageBitmap(itSignPostImage)
            bDisplayRoadCode = false
            bDisplayRouteInstruction = false
        } ?: run { sign_post.visibility = View.GONE }

        if (bDisplayRoadCode) {
            roadCodeImage?.let { itRoadCodeImage ->
                road_code.visibility = View.VISIBLE
                road_code.setImageBitmap(itRoadCodeImage)

                if (itRoadCodeImage.height > 0) {
                    val ratio: Float =
                        (itRoadCodeImage.width).toFloat() / itRoadCodeImage.height
                    road_code.layoutParams.width =
                        (road_code.layoutParams.height * ratio).toInt()
                }

                bDisplayedRoadCode = true
            } ?: run { road_code.visibility = View.GONE }
        } else {
            road_code.visibility = View.GONE
        }

        if (bDisplayRouteInstruction) {
            val turnInstruction = turnInstruction
            if (!turnInstruction.isNullOrEmpty()) {
                turn_instruction.visibility = View.VISIBLE
                turn_instruction.text = turnInstruction
                if (bDisplayedRoadCode) {
                    turn_instruction.maxLines = 1
                } else {
                    turn_instruction.maxLines = 3
                }
            } else {
                turn_instruction.visibility = View.GONE
            }
        } else {
            turn_instruction.visibility = View.GONE
        }
    }

    private fun updateTrafficPanel() {
        if (trafficEvent == null) {
            traffic_panel.visibility = View.GONE
            return
        }

        traffic_panel.visibility = View.VISIBLE
        if (currentAlarmedMarker != null) {
            traffic_panel.background =
                context?.resources?.getDrawable(R.drawable.white_button, null)

            val layoutParams: FrameLayout.LayoutParams =
                traffic_image.layoutParams as FrameLayout.LayoutParams

            val margin: Int = navigationPanelPadding
            layoutParams.setMargins(margin, margin, margin, margin)
            end_of_section.layoutParams = layoutParams
        } else {
            traffic_panel.background =
                context?.resources?.getDrawable(
                    R.drawable.bottom_rounded_white_button,
                    null
                )

            val layoutParams: FrameLayout.LayoutParams =
                traffic_image.layoutParams as FrameLayout.LayoutParams
            val margin: Int = navigationPanelPadding
            val top: Int =
                navigationPanelPadding - Util.getSizeInPixels(context, 1)
            layoutParams.setMargins(margin, top, margin, margin)
            end_of_section.layoutParams = layoutParams
        }

        setPanelBackground(
            traffic_panel.background,
            trafficPanelBackgroundColor
        )

        if (trafficImage != null) {
            traffic_image.setImageBitmap(trafficImage)
        }

        if (bInsideTrafficEvent) {
            if (endOfSectionImage != null) {
                end_of_section.visibility = View.VISIBLE
                end_of_section.setImageBitmap(endOfSectionImage)
            } else {
                end_of_section.visibility = View.GONE
            }
        } else {
            end_of_section.visibility = View.GONE
        }

        traffic_event_description.text = trafficEventDescription

        var prefix = distanceToTrafficPrefix
        if (!prefix.isNullOrEmpty()) {
            prefix = "$prefix "
        }
        distance_to_traffic_prefix.text = prefix

        distance_to_traffic.text = distanceToTraffic
        distance_to_traffic_unit.text = distanceToTrafficUnit

        traffic_delay_time.text = trafficDelayTime
        traffic_delay_time_unit.text = trafficDelayTimeUnit

        if (!trafficDelayDistance.isNullOrEmpty()) {
            traffic_delay_distance.text = trafficDelayDistance
            traffic_delay_distance.visibility = View.VISIBLE
        } else {
            traffic_delay_distance.visibility = View.GONE
        }

        if (!trafficDelayDistanceUnit.isNullOrEmpty()) {
            traffic_delay_distance_unit.text = trafficDelayDistanceUnit
            traffic_delay_distance_unit.visibility = View.VISIBLE
        } else {
            traffic_delay_distance_unit.visibility = View.GONE
        }
    }

    private fun updateAlarmPanel() {
        if (currentAlarmedMarker == null) {
            alarm_panel.visibility = View.GONE
            return
        }

        alarm_panel.visibility = View.VISIBLE

        alarm_icon_container.layoutParams.height =
            ViewGroup.LayoutParams.WRAP_CONTENT
        alarm_distance_container.setPadding(0, 0, 0, 0)
        voting_panel.visibility = View.GONE
        alarm_score.visibility = View.GONE

        alarm_panel.background = context?.resources?.getDrawable(
            R.drawable.bottom_rounded_white_button,
            null
        )
        setPanelBackground(alarm_panel.background, Color.WHITE)

        val safetyCameraAlarmImage = GEMSdkCall.execute {
            getSafetyCameraAlarmImage(currentAlarmedMarker, navigationImageSize).second
        }

        alarm_icon.setImageBitmap(safetyCameraAlarmImage)
        safetyCameraAlarmImage?.let { itSafetyCameraAlarmImage ->
            if (itSafetyCameraAlarmImage.height > 0) {
                val ratio =
                    itSafetyCameraAlarmImage.width.toFloat() / itSafetyCameraAlarmImage.height
                alarm_icon.layoutParams.width =
                    (alarm_icon.layoutParams.height * ratio).toInt()
            }
        }

        if (distanceToSafetyCameraAlarm != null) {
            distance_to_alarm.visibility = View.VISIBLE
            distance_to_alarm.text = distanceToSafetyCameraAlarm
            distance_to_alarm.setTextColor(Color.BLACK)

            distanceToSafetyCameraAlarmUnit?.let { unitText ->
                distance_to_alarm_unit.visibility = View.VISIBLE
                distance_to_alarm_unit.text = unitText
                distance_to_alarm_unit.setTextColor(Color.BLACK)
            } ?: run { distance_to_alarm_unit.visibility = View.GONE }
        } else {
            distance_to_alarm.visibility = View.GONE
            distance_to_alarm_unit.visibility = View.GONE
        }
    }

    // ----------------------------------------------------------------------------------------------
    private var alarmService: AlarmService? = null
    private var navInstr: NavigationInstruction? = null
    private var route: Route? = null

    private var nDistanceToTrafficEvent = 0
    private var nRemainingDistanceInsideTrafficEvent = 0
    private var trafficEvent: RouteTrafficEvent? = null
    private var bInsideTrafficEvent = false
    private var trafficEventDescription: String? = null
    private var distanceToTrafficPrefix: String? = null
    private var trafficDelayTime: String? = null
    private var trafficDelayTimeUnit: String? = null
    private var trafficDelayDistance: String? = null
    private var trafficDelayDistanceUnit: String? = null
    private var distanceToTraffic: String? = null
    private var distanceToTrafficUnit: String? = null

    private var distanceToSafetyCameraAlarmUnit: String? = null
    private var distanceToSafetyCameraAlarm: String? = null
    private var currentAlarmedMarker: Marker? = null
    private var turnInstruction: String? = null
    private var distanceToNextTurn: String? = null
    private var distanceToNextTurnUnit: String? = null

    private var turnImage: Bitmap? = null
    private var roadCodeImage: Bitmap? = null
    private var signPostImage: Bitmap? = null
    private var trafficImage: Bitmap? = null

    private var statusText: String? = null

    private fun reset() {
        this.visibility = View.GONE
        alarmService = null
        route = null
        navInstr = null
        currentAlarmedMarker = null
        turnImage = null
        roadCodeImage = null
        signPostImage = null
        trafficImage = null
        turnInstruction = ""
        distanceToNextTurn = ""
        distanceToNextTurnUnit = ""
        nDistanceToTrafficEvent = 0
        nRemainingDistanceInsideTrafficEvent = 0
        trafficEvent = null
        bInsideTrafficEvent = false
        trafficEventDescription = null
        distanceToTrafficPrefix = null
        trafficDelayTime = null
        trafficDelayTimeUnit = null
        trafficDelayDistance = null
        trafficDelayDistanceUnit = null
        distanceToTraffic = null
        distanceToTrafficUnit = null
        distanceToSafetyCameraAlarmUnit = null
        distanceToSafetyCameraAlarm = null
        statusText = null
    }

    /**---------------------------------------------------------------------------------------------
    Get Images
    ----------------------------------------------------------------------------------------------*/

    fun getRoadCodeImage(from: NavigationInstruction?, width: Int, height: Int):
        Pair<Int, Bitmap?> {
        val navInstr = from ?: return Pair(0, null)
        val roadsInfo = navInstr.getNextRoadInformation() ?: return Pair(0, null)
        if (roadsInfo.isEmpty()) return Pair(0, null)

        var resultWidth = width
        if (resultWidth == 0) {
            resultWidth = (2.5 * height).toInt()
        }

        val image = GEMSdkCall.execute { navInstr.getRoadInfoImage(roadsInfo) }

        val resultPair =
            Util.createBitmap(image, resultWidth, height)

        resultWidth = resultPair.first
        return Pair(resultWidth, resultPair.second)
    }

    fun getSafetyCameraAlarmImage(from: Marker?, height: Int): Pair<Int, Bitmap?> {
        GEMSdkCall.checkCurrentThread()
        val marker = from ?: return Pair(0, null)

        val aspectRatio = Util.getImageAspectRatio(marker)
        val actualWidth = (aspectRatio * height).toInt()

        val image = marker.getImage()

        return Pair(actualWidth, Util.createBitmap(image, actualWidth, height))
    }

    fun getTrafficEndOfSectionIcon(width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()
        return Util.getImageIdAsBitmap(
            GemIcons.Other_UI.Traffic_EndOfSection_Square.value,
            width,
            height
        )
    }

    fun getNextTurnImage(from: NavigationInstruction?, width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()
        val navInstr = from ?: return null
        if (!navInstr.hasNextTurnInfo()) return null

        val image = navInstr.getNextTurnDetails()?.getAbstractGeometryImage()

        val aInner = TRgba(255, 255, 255, 255)
        val aOuter = TRgba(0, 0, 0, 255)
        val iInner = TRgba(128, 128, 128, 255)
        val iOuter = TRgba(128, 128, 128, 255)

        return Util.createBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
    }

    fun getTurnImage(from: RouteInstruction?, width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()
        val navInstr = from ?: return null
        if (!navInstr.hasTurnInfo()) return null

        val image = navInstr.getTurnDetails()?.getAbstractGeometryImage()

        val aInner = TRgba(255, 255, 255, 255)
        val aOuter = TRgba(0, 0, 0, 255)
        val iInner = TRgba(128, 128, 128, 255)
        val iOuter = TRgba(128, 128, 128, 255)

        return Util.createBitmap(image, width, height, aInner, aOuter, iInner, iOuter)
    }

    fun getSignpostImage(
        from: NavigationInstruction?,
        width: Int,
        height: Int
    ): Pair<Int, Bitmap?> {
        GEMSdkCall.checkCurrentThread()
        val navInstr = from ?: return Pair(0, null)
        if (!navInstr.hasSignpostInfo()) return Pair(0, null)

        var resultWidth = width
        if (resultWidth == 0) {
            resultWidth = (2.5 * height).toInt()
        }

        val image = navInstr.getSignpostDetails()?.getImage()

        val resultPair = Util.createBitmap(image, resultWidth, height)
        resultWidth = resultPair.first

        return Pair(resultWidth, resultPair.second)
    }

    fun getTrafficImage(from: RouteTrafficEvent?, width: Int, height: Int): Bitmap? {
        GEMSdkCall.checkCurrentThread()
        return Util.createBitmap(from?.getImage(), width, height)
    }
}
