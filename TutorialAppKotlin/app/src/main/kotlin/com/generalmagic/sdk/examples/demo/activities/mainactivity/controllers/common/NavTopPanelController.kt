/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import com.generalmagic.sdk.*
import com.generalmagic.sdk.core.Rgba
import com.generalmagic.sdk.core.SdkSettings
import com.generalmagic.sdk.d3scene.OverlayItem
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.Util
import com.generalmagic.sdk.examples.demo.util.Util.setPanelBackground
import com.generalmagic.sdk.examples.demo.util.UtilUITexts
import com.generalmagic.sdk.routesandnavigation.*
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.SdkUtil.getDistText
import com.generalmagic.sdk.util.SdkUtil.getTimeText
import com.generalmagic.sdk.util.SdkUtil.getUIString
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.UtilGemImages


class NavTopPanelController(context: Context, attrs: AttributeSet?) :
    RelativeLayout(context, attrs) {

    private val navInfoPanelFactor = 0.45

    private val turnImageSize =
        context.resources?.getDimension(R.dimen.nav_top_panel_big_img_size)?.toInt() ?: 0

    private val navigationPanelPadding =
        context.resources?.getDimension(R.dimen.nav_top_panel_padding)?.toInt() ?: 0

    private val signPostImageSize =
        context.resources?.getDimension(R.dimen.signPostImageSize)?.toInt() ?: 0

    val navigationImageSize =
        context.resources?.getDimension(R.dimen.navigationImageSize)?.toInt() ?: 0

    private var endOfSectionImage =
        getTrafficEndOfSectionIcon(navigationImageSize, navigationImageSize)

    private val trafficPanelBackgroundColor = Color.rgb(255, 175, 63)

    class TSameImage(var value: Boolean = false)

    private var mLastTurnImageId = Long.MAX_VALUE
    private var mLastAlarmImageId = Long.MAX_VALUE
    private var mLastTrafficImageId = Long.MAX_VALUE

    private var bSameTurnImage = false
    private var bSameAlarmImage = false
    private var bSameTrafficImage = false

    lateinit var status_text: TextView
    lateinit var turn_instruction: TextView
    lateinit var turn_image: ImageView
    lateinit var turn_distance: TextView
    lateinit var turn_distance_unit: TextView
    lateinit var sign_post: ImageView
    lateinit var road_code: ImageView
    lateinit var traffic_panel: LinearLayout
    lateinit var traffic_image: ImageView
    lateinit var traffic_event_description: TextView
    lateinit var traffic_delay_distance: TextView
    lateinit var traffic_delay_distance_unit: TextView
    lateinit var traffic_delay_time: TextView
    lateinit var traffic_delay_time_unit: TextView
    lateinit var end_of_section: ImageView
    lateinit var distance_to_traffic: TextView
    lateinit var distance_to_traffic_prefix: TextView
    lateinit var distance_to_traffic_unit: TextView
    lateinit var alarm_panel: LinearLayout
    lateinit var alarm_distance_container: LinearLayout
    lateinit var alarm_score: TextView
    lateinit var distance_to_alarm: TextView
    lateinit var distance_to_alarm_unit: TextView
    lateinit var alarm_icon_container: RelativeLayout
    lateinit var alarm_icon: ImageView
    lateinit var voting_panel: LinearLayout

    override fun onFinishInflate() {
        super.onFinishInflate()
        status_text = findViewById(R.id.status_text)
        turn_instruction = findViewById(R.id.turn_instruction)
        turn_image = findViewById(R.id.turn_image)
        turn_distance = findViewById(R.id.turn_distance)
        turn_distance_unit = findViewById(R.id.turn_distance_unit)
        sign_post = findViewById(R.id.sign_post)
        road_code = findViewById(R.id.road_code)
        traffic_panel = findViewById(R.id.traffic_panel)
        traffic_image = findViewById(R.id.traffic_image)
        traffic_event_description = findViewById(R.id.traffic_event_description)
        traffic_delay_distance = findViewById(R.id.traffic_delay_distance)
        traffic_delay_distance_unit = findViewById(R.id.traffic_delay_distance_unit)
        traffic_delay_time = findViewById(R.id.traffic_delay_time)
        traffic_delay_time_unit = findViewById(R.id.traffic_delay_time_unit)
        end_of_section = findViewById(R.id.end_of_section)
        distance_to_traffic = findViewById(R.id.distance_to_traffic)
        distance_to_traffic_prefix = findViewById(R.id.distance_to_traffic_prefix)
        distance_to_traffic_unit = findViewById(R.id.distance_to_traffic_unit)
        alarm_panel = findViewById(R.id.alarm_panel)
        alarm_distance_container = findViewById(R.id.alarm_distance_container)
        alarm_score = findViewById(R.id.alarm_score)
        distance_to_alarm = findViewById(R.id.distance_to_alarm)
        distance_to_alarm_unit = findViewById(R.id.distance_to_alarm_unit)
        alarm_icon_container = findViewById(R.id.alarm_icon_container)
        alarm_icon = findViewById(R.id.alarm_icon)
        voting_panel = findViewById(R.id.voting_panel)
    }

    fun update(navInstr: NavigationInstruction?, route: Route?, alarmService: AlarmService?) {
        reset()

        SdkCall.execute {
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

        SdkCall.execute {
            updateNavigationInfo(instruction)
        }

        updateNavigationTopPanel()
    }

    fun update(event: RouteTrafficEvent, routeLength: Int) {
        reset()

        SdkCall.execute {
            this.trafficEvent = event
            updateNavigationInfo(trafficEvent, routeLength)
        }

        updateNavigationTopPanel()
    }

    private fun updateNavigationInfo(navInstr: NavigationInstruction?) {
        SdkCall.checkCurrentThread()

        if (navInstr == null) return

        if (navInstr.navigationStatus != ENavigationStatus.Running) {
            return
        }

        val bHasNextRoadCode = (navInstr.nextRoadInformation?.size ?: 0) > 0
        val turnDetails = navInstr.nextTurnDetails

        if (turnDetails != null &&
            (turnDetails.event == ETurnEvent.Stop || turnDetails.event == ETurnEvent.Intermediate)
        ) {
            turnInstruction = navInstr.nextTurnInstruction ?: ""
        } else if (navInstr.hasSignpostInfo()) {
            turnInstruction = navInstr.signpostInstruction ?: ""
            if (!turnInstruction.isNullOrEmpty()) {
                turnInstruction = navInstr.nextStreetName ?: ""
            }
        } else {
            turnInstruction = navInstr.nextStreetName ?: ""
        }

        if (!turnInstruction.isNullOrEmpty() && !bHasNextRoadCode) {
            turnInstruction = navInstr.nextTurnInstruction ?: ""
        }

        val distanceToNextTurnTexts = getDistText(
            navInstr.timeDistanceToNextTurn?.totalDistance ?: 0,
            SdkSettings().unitSystem,
            true
        )

        // sizes
        val screen = GEMApplication.gemMapScreen()
        val surfaceWidth = screen?.viewport?.width ?: 0
        val surfaceHeight = screen?.viewport?.height ?: 0

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

        val sameImage = TSameImage()
        val newTurnImage = getNextTurnImage(navInstr, turnImageSize, turnImageSize, sameImage)
        if (!sameImage.value) {
            turnImage = newTurnImage
            bSameTurnImage = false
        } else {
            bSameTurnImage = true
        }

        signPostImage = getSignpostImage(
            navInstr, availableWidthForMiddlePanel, signPostImageSize
        ).second

        roadCodeImage = if (signPostImage == null) {
            getRoadCodeImage(navInstr, availableWidthForMiddlePanel, navigationImageSize).second
        } else {
            null
        }
    }

    private fun updateNavigationInfo(event: RouteTrafficEvent?, routeLength: Int) {
        SdkCall.checkCurrentThread()

        updateTrafficEvent(event)

        val remainingDistance = routeLength - (event?.distanceToDestination ?: 0)

        val distFromStartTexts = getDistText(
            remainingDistance,
            SdkSettings().unitSystem,
            true
        )

        val text = String.format(
            "%s\n%s",
            UtilUITexts.formatTrafficDelayAndLength(event),
            event?.description ?: ""
        )

        turnImage = trafficImage
        turnInstruction = text
        distanceToNextTurn = distFromStartTexts.first
        distanceToNextTurnUnit = distFromStartTexts.second
    }

    private fun updateNavigationInfo(event: RouteInstruction?) {
        SdkCall.checkCurrentThread()

        val distanceToNextTurnTexts = getDistText(
            event?.traveledTimeDistance?.totalDistance ?: 0,
            SdkSettings().unitSystem,
            true
        )

        val sameImage = TSameImage()
        val newTurnImage = getTurnImage(event, turnImageSize, turnImageSize, sameImage)
        if (!sameImage.value) {
            turnImage = newTurnImage
            bSameTurnImage = false
        } else {
            bSameTurnImage = true
        }

        turnInstruction = event?.turnInstruction ?: ""
        distanceToNextTurn = distanceToNextTurnTexts.first
        distanceToNextTurnUnit = distanceToNextTurnTexts.second
    }

    private fun updateTrafficEvent(trafficEvent: RouteTrafficEvent?) {
        SdkCall.checkCurrentThread()

        if (trafficEvent == null) return

        trafficEventDescription = trafficEvent.description ?: ""

        val distance = if (bInsideTrafficEvent) {
            nRemainingDistanceInsideTrafficEvent
        } else {
            nDistanceToTrafficEvent
        }

        val distanceToTrafficPair = getDistText(
            distance,
            SdkSettings().unitSystem,
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
                if (trafficEvent.length > 0) {
                    val nRemainingTimeInsideTrafficEvent = (
                        trafficEvent.delay *
                            nRemainingDistanceInsideTrafficEvent
                        ) / trafficEvent.length

                    val trafficDelayTextPair = getTimeText(nRemainingTimeInsideTrafficEvent)

                    trafficDelayTime = trafficDelayTextPair.first
                    trafficDelayTimeUnit = trafficDelayTextPair.second
                }
            } else {
                val trafficDistTextPair = getDistText(
                    trafficEvent.length,
                    SdkSettings().unitSystem,
                    true
                )

                trafficDelayDistance = trafficDistTextPair.first
                trafficDelayDistanceUnit = trafficDistTextPair.second

                val trafficDelayTextPair = getTimeText(trafficEvent.delay)

                trafficDelayTime = String.format("+%s", trafficDelayTextPair.first)
                trafficDelayTimeUnit = trafficDelayTextPair.second
            }
        }

        val sameImage = TSameImage()
        val newTrafficImage =
            getTrafficImage(trafficEvent, navigationImageSize, navigationImageSize, sameImage)
        if (!sameImage.value) {
            trafficImage = newTrafficImage
            bSameTrafficImage = false
        } else {
            bSameTrafficImage = true
        }
    }

    private fun pickTrafficEvent(
        navInstr: NavigationInstruction?,
        route: Route?
    ): RouteTrafficEvent? {
        SdkCall.checkCurrentThread()

        if (navInstr == null || route == null) return null

        if (navInstr.navigationStatus != ENavigationStatus.Running) return null
        val trafficEventsList = route.trafficEvents ?: return null

        val remainingTravelDistance =
            navInstr.remainingTravelTimeDistance?.totalDistance ?: 0

        // pick current traffic event
        for (event in trafficEventsList) {
            val distToDest = event.distanceToDestination
            nDistanceToTrafficEvent =
                remainingTravelDistance - distToDest

            if (nDistanceToTrafficEvent <= 0) {
                nRemainingDistanceInsideTrafficEvent =
                    event.length - (distToDest - remainingTravelDistance)

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
        SdkCall.checkCurrentThread()
        alarmService ?: return

        val maxDistanceToAlarm = alarmService.alarmDistance

        val markersList = alarmService.overlayItemAlarms
        if (markersList == null || markersList.size == 0) return

        val distance = markersList.getDistance(0)
        if (distance < maxDistanceToAlarm) {
            val textsPair = getDistText(distance.toInt(), SdkSettings().unitSystem, true)

            distanceToSafetyCameraAlarm = textsPair.first
            distanceToSafetyCameraAlarmUnit = textsPair.second

            currentAlarmedMarker = markersList.getItem(0)
        }
    }

    private fun updateNavigationTopPanel() {
        this.visibility = View.VISIBLE
        GEMApplication.setAppBarVisible(false)
        GEMApplication.setSystemBarsVisible(false)
        GEMApplication.setScreenAlwaysOn(true)

        var bDisplayRoadCode = true
        var bDisplayRouteInstruction = true
        var bDisplayedRoadCode = false

        statusText?.let {
            status_text.visibility = View.VISIBLE
            status_text.text = it
        } ?: run { status_text.visibility = View.GONE }

        if (!bSameTurnImage) {
            turn_image.setImageBitmap(turnImage)
        }

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
                ContextCompat.getDrawable(context, R.drawable.white_button)

            val layoutParams: FrameLayout.LayoutParams =
                traffic_image.layoutParams as FrameLayout.LayoutParams

            val margin: Int = navigationPanelPadding
            layoutParams.setMargins(margin, margin, margin, margin)
            end_of_section.layoutParams = layoutParams
        } else {
            traffic_panel.background =
                ContextCompat.getDrawable(context, R.drawable.bottom_rounded_white_button)

            val layoutParams: FrameLayout.LayoutParams =
                traffic_image.layoutParams as FrameLayout.LayoutParams
            val margin: Int = navigationPanelPadding
            val top: Int =
                navigationPanelPadding - Util.getSizeInPixels(1)
            layoutParams.setMargins(margin, top, margin, margin)
            end_of_section.layoutParams = layoutParams
        }

        setPanelBackground(
            traffic_panel.background,
            trafficPanelBackgroundColor
        )

        if (trafficImage != null && !bSameTrafficImage) {
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

        alarm_panel.background =
            ContextCompat.getDrawable(context, R.drawable.bottom_rounded_white_button)
        setPanelBackground(alarm_panel.background, Color.WHITE)

        val sameImage = TSameImage()
        val safetyCameraAlarmImage =
            getSafetyCameraAlarmImage(currentAlarmedMarker, navigationImageSize, sameImage).second
        if (sameImage.value) {
            bSameAlarmImage = true
        }

        safetyCameraAlarmImage?.let { itSafetyCameraAlarmImage ->
            if (!bSameAlarmImage) {
                alarm_icon.setImageBitmap(itSafetyCameraAlarmImage)
            }

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
    private var currentAlarmedMarker: OverlayItem? = null
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

    private fun getRoadCodeImage(from: NavigationInstruction?, width: Int, height: Int):
        Pair<Int, Bitmap?> {
        return SdkCall.execute {
            val navInstr = from ?: return@execute Pair(0, null)
            val roadsInfo = navInstr.nextRoadInformation ?: return@execute Pair(0, null)
            if (roadsInfo.isEmpty()) return@execute Pair(0, null)

            var resultWidth = width
            if (resultWidth == 0) {
                resultWidth = (2.5 * height).toInt()
            }

            val image = SdkCall.execute { navInstr.getRoadInfoImage(roadsInfo) }

            val resultPair = UtilGemImages.asBitmap(image, resultWidth, height)

            resultWidth = resultPair.first
            return@execute Pair(resultWidth, resultPair.second)
        } ?: Pair(0, null)
    }

    private fun getSafetyCameraAlarmImage(
        from: OverlayItem?,
        height: Int,
        bSameImage: TSameImage
    ): Pair<Int, Bitmap?> {
        return SdkCall.execute {
            val marker = from ?: return@execute Pair(0, null)
            if (marker.image?.uid ?: 0 == mLastAlarmImageId) {
                bSameImage.value = true
                return@execute Pair(0, null)
            }

            val aspectRatio = Util.getImageAspectRatio(marker)
            val actualWidth = (aspectRatio * height).toInt()

            val image = marker.image
            if (image != null) {
                mLastAlarmImageId = image.uid
            }

            return@execute Pair(actualWidth, UtilGemImages.asBitmap(image, actualWidth, height))
        } ?: Pair(0, null)
    }

    private fun getTrafficEndOfSectionIcon(width: Int, height: Int): Bitmap? {
        return SdkCall.execute {
            return@execute UtilGemImages.asBitmap(
                SdkImages.UI.Traffic_EndOfSection_Square.value, width, height
            )
        }
    }

    private fun getNextTurnImage(
        from: NavigationInstruction?,
        width: Int,
        height: Int,
        bSameImage: TSameImage
    ): Bitmap? {
        return SdkCall.execute {
            val navInstr = from ?: return@execute null
            if (!navInstr.hasNextTurnInfo()) return@execute null
            if (navInstr.nextTurnDetails?.abstractGeometryImage
                    ?.uid ?: 0 == mLastTurnImageId
            ) {
                bSameImage.value = true
                return@execute null
            }

            val image = navInstr.nextTurnDetails?.abstractGeometryImage
            if (image != null) {
                mLastTurnImageId = image.uid
            }

            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            return@execute UtilGemImages.asBitmap(
                image,
                width,
                height,
                aInner,
                aOuter,
                iInner,
                iOuter
            )
        }
    }

    private fun getTurnImage(
        from: RouteInstruction?,
        width: Int,
        height: Int,
        bSameImage: TSameImage
    ): Bitmap? {
        return SdkCall.execute {
            val navInstr = from ?: return@execute null
            if (!navInstr.hasTurnInfo()) return@execute null
            if (navInstr.turnDetails?.abstractGeometryImage
                    ?.uid ?: 0 == mLastTurnImageId
            ) {
                bSameImage.value = true
                return@execute null
            }

            val image = navInstr.turnDetails?.abstractGeometryImage
            if (image != null) {
                val details = navInstr.turnDetails
                mLastTurnImageId = details?.abstractGeometryImage?.uid ?: 0
            }

            val aInner = Rgba(255, 255, 255, 255)
            val aOuter = Rgba(0, 0, 0, 255)
            val iInner = Rgba(128, 128, 128, 255)
            val iOuter = Rgba(128, 128, 128, 255)

            return@execute UtilGemImages.asBitmap(
                image,
                width,
                height,
                aInner,
                aOuter,
                iInner,
                iOuter
            )
        }
    }

    private fun getSignpostImage(
        from: NavigationInstruction?, width: Int, height: Int
    ): Pair<Int, Bitmap?> {
        return SdkCall.execute {
            val navInstr = from ?: return@execute Pair(0, null)
            if (!navInstr.hasSignpostInfo()) return@execute Pair(0, null)
            val image = navInstr.signpostDetails?.image ?: return@execute Pair(0, null)

            return@execute UtilGemImages.asBitmap(image, width, height)
        } ?: Pair(0, null)
    }

    private fun getTrafficImage(
        from: RouteTrafficEvent?,
        width: Int,
        height: Int,
        sameImage: TSameImage
    ): Bitmap? {
        return SdkCall.execute {
            if (from?.image?.uid ?: 0 == mLastTrafficImageId) {
                sameImage.value = true
                return@execute null
            }

            val image = from?.image
            if (image != null) {
                mLastTrafficImageId = image.uid
            }

            UtilGemImages.asBitmap(image, width, height)
        }
    }
}
