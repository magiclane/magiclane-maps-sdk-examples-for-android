/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.util

import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.demo.app.GEMApplication
import com.generalmagic.sdk.demo.util.Utils.Companion.getDistText
import com.generalmagic.sdk.demo.util.Utils.Companion.getTimeText
import com.generalmagic.sdk.demo.util.Utils.Companion.getUIString
import com.generalmagic.sdk.routingandnavigation.Route
import com.generalmagic.sdk.routingandnavigation.RouteTrafficEvent
import com.generalmagic.sdk.util.GEMSdkCall
import com.generalmagic.sdk.util.SdkIcons
import com.generalmagic.sdk.util.StringIds
import java.util.*

class UtilUITexts {
    companion object {
        fun formatTrafficDelayAndLength(length: Int, delay: Int, isRoadblock: Boolean): String {
            val distText = getDistText(length, CommonSettings().getUnitSystem())

            return if (!isRoadblock) {
                val delayText = getTimeText(delay)

                String.format(
                    "%s %s, %s %s",
                    delayText.first,
                    delayText.second,
                    distText.first,
                    distText.second
                )
            } else {
                String.format(
                    "%s, %s %s",
                    getUIString(StringIds.eStrRoadBlock),
                    distText.first,
                    distText.second
                )
            }
        }

        fun formatTrafficDelayAndLength(event: RouteTrafficEvent?): String {
            return formatTrafficDelayAndLength(
                event?.getLength() ?: 0,
                event?.getDelay() ?: 0,
                event?.isRoadblock() ?: false
            )
        }

        fun formatSizeAsText(inSizeInBytes: Long): String {
            var sizeInBytes: Double = inSizeInBytes.toDouble()
            var unit: String

            if (sizeInBytes < (1024.0 * 1024.0)) {
                sizeInBytes /= 1024.0
                unit = getUIString(StringIds.eStrKiloByte)
            } else if (sizeInBytes < (1024.0 * 1024.0 * 1024.0)) {
                sizeInBytes /= (1024.0 * 1024.0)
                unit = getUIString(StringIds.eStrMegaByte)
            } else {
                sizeInBytes /= (1024.0 * 1024.0 * 1024.0)
                unit = getUIString(StringIds.eStrGigaByte)
            }

            var size = String.format("%.2f", sizeInBytes)

            val pos = size.indexOf(".00")
            if ((pos > 0) && (pos == (size.length - 3))) {
                size = size.substring(0, pos)
            }

            return String.format("%s %s", size, unit)
        }

        fun formatRouteName(route: Route): String {
            val timeDistance = route.getTimeDistance() ?: return ""
            val distInMeters = timeDistance.getTotalDistance()
            val timeInSeconds = timeDistance.getTotalTime()

            val distTextPair = getDistText(
                distInMeters,
                CommonSettings().getUnitSystem(),
                bHighResolution = true
            )

            val timeTextPair = getTimeText(timeInSeconds)

            return String.format("%%%%0%%%% ${distTextPair.first} ${distTextPair.second} \n%%%%-1%%%% ${timeTextPair.first} ${timeTextPair.second} %%%%1%%%% %%%%2%%%%")
        }

        const val MILES_TO_KM = 1.6093
        const val MILES_TO_METERS = 1609.26939169
        const val KM_TO_MILES = 0.6214
        const val METERS_TO_MILES = 0.0006214
        const val FT_TO_METERS = 0.3048
        const val METERS_TO_FT = 3.2808
        const val YARDS_TO_METERS = 0.9144
        const val METERS_TO_YARDS = 1.0936
        const val MILES_TO_YARDS = 1760
        const val YARDS_TO_MILES = 0.00056818

        fun getFormattedDistanceTime(distanceInMeters: Int, timeInSeconds: Int): String {
            val dist = getDistText(distanceInMeters, CommonSettings().getUnitSystem(), true)

            val time = formatTime(timeInSeconds)

            return String.format("%s %s - %s", dist.first, dist.second, time)
        }

        fun formatTime(inputSeconds: Int): String {
            var timeInSeconds = inputSeconds
            val seconds: Int = timeInSeconds % 60
            timeInSeconds /= 60
            val minutes: Int = timeInSeconds % 60
            timeInSeconds /= 60

            return when {
                timeInSeconds > 0 -> {
                    // hours, minutes
                    String.format(
                        getUIString(StringIds.eStrTempTimeFormatHour),
                        timeInSeconds,
                        minutes
                    ) // eStrTempTimeFormatHour
                }
                minutes > 0 -> {
                    // minutes
                    String.format(getUIString(StringIds.eStrTempTimeFormatMin), minutes)
                }
                else -> {
                    // seconds
                    String.format(getUIString(StringIds.eStrTempTimeFormatSec), seconds)
                }
            }
        }

        fun fillLandmarkAddressInfo(landmark: Landmark, bFillAllFields: Boolean = false) {
            GEMSdkCall.execute {
                val landmarkAddress = landmark.getAddress() ?: AddressInfo()

                val street = landmarkAddress.getField(EAddressField.EStreetName) ?: ""
                val city = landmarkAddress.getField(EAddressField.ECity) ?: ""
                val settlement = landmarkAddress.getField(EAddressField.ESettlement) ?: ""
                val county = landmarkAddress.getField(EAddressField.ECounty) ?: ""

                if (bFillAllFields || (city.isEmpty() && settlement.isEmpty() && county.isEmpty())) {
                    var landmarkList = ArrayList<Landmark>()
                    val coords = landmark.getCoordinates()
                    if (coords != null) {
                        val mainView = GEMApplication.getMainMapView()
                        landmarkList = mainView?.getNearestLocations(coords) ?: landmarkList
                    }

                    if (landmarkList.isNotEmpty()) {
                        val lmk = landmarkList[0]
                        val lmkAddress = lmk.getAddress()

                        val aStreet = lmkAddress?.getField(EAddressField.EStreetName) ?: ""
                        val aCity = lmkAddress?.getField(EAddressField.ECity) ?: ""
                        val aSettlement = lmkAddress?.getField(EAddressField.ESettlement) ?: ""
                        val aCounty = lmkAddress?.getField(EAddressField.ECounty) ?: ""

                        if (street.isEmpty() && aStreet.isNotEmpty()) {
                            landmarkAddress.setField(aStreet, EAddressField.EStreetName)
                        }

                        if (city.isEmpty() && aCity.isNotEmpty()) {
                            landmarkAddress.setField(aCity, EAddressField.ECity)
                        }

                        if (settlement.isEmpty() && aSettlement.isNotEmpty()) {
                            landmarkAddress.setField(aSettlement, EAddressField.ESettlement)
                        }

                        if (county.isEmpty() && aCounty.isNotEmpty()) {
                            landmarkAddress.setField(aCounty, EAddressField.ECounty)
                        }

                        if (bFillAllFields) {
                            val aCountry = lmkAddress?.getField(EAddressField.ECountry) ?: ""
                            val aCountryCode =
                                lmkAddress?.getField(EAddressField.ECountryCode) ?: ""
                            val country = landmarkAddress.getField(EAddressField.ECountry) ?: ""
                            val countryCode =
                                landmarkAddress.getField(EAddressField.ECountryCode) ?: ""

                            if (country.isEmpty() && aCountry.isNotEmpty()) {
                                landmarkAddress.setField(aCountry, EAddressField.ECountry)
                            }

                            if (countryCode.isEmpty() && aCountryCode.isNotEmpty()) {
                                landmarkAddress.setField(aCountryCode, EAddressField.ECountryCode)
                            }
                        }
                    }
                }
            }
        }

        fun formatLandmarkDetails(landmark: Landmark, excludeCountry: Boolean = false): String {
            return GEMSdkCall.execute {
                val formatted = pairFormatLandmarkDetails(landmark, excludeCountry)

                return@execute if (formatted.second.isNotEmpty()) {
                    String.format("%s, %s", formatted.first, formatted.second)
                } else {
                    formatted.first
                }
            } ?: ""
        }

        fun formatName(landmark: Landmark): String {
            return GEMSdkCall.execute {
                var name = "" // result

                var str = landmark.getName() ?: ""

                if (str.isNotEmpty()) {
                    name = str
                    return@execute name
                }

                val landmarkAddress = landmark.getAddress()
                str = landmarkAddress?.getField(EAddressField.EStreetName) ?: ""

                if (str.isNotEmpty()) {
                    val includeList = arrayListOf(
                        EAddressField.EStreetName.value,
                        EAddressField.EStreetNumber.value
                    )

                    name = landmarkAddress?.format(ArrayList(), includeList) ?: name
                    return@execute name
                }

                str = landmarkAddress?.getField(EAddressField.ECity) ?: ""
                if (str.isNotEmpty()) {
                    name = str
                    val image = landmark.getImage() ?: return@execute name
                    if (image.getUid() == SdkIcons.Other_UI.LocationDetails_SendDetails.value.toLong()) {
                        val postalCode = landmarkAddress?.getField(EAddressField.EPostalCode) ?: ""

                        if (postalCode.isNotEmpty()) {
                            name += " "
                            name += landmarkAddress?.getField(EAddressField.EPostalCode)
                        }
                    }
                    return@execute name
                }

                str = landmarkAddress?.getField(EAddressField.ESettlement) ?: ""
                if (str.isNotEmpty()) {
                    name = str
                    return@execute name
                }

                str = landmarkAddress?.getField(EAddressField.ECounty) ?: ""
                if (str.isNotEmpty()) {
                    name = str
                    return@execute name
                }

                str = landmarkAddress?.getField(EAddressField.EState) ?: ""
                if (str.isNotEmpty()) {
                    name = str
                    return@execute name
                }

                str = landmarkAddress?.getField(EAddressField.ECountry) ?: ""
                if (str.isNotEmpty()) {
                    name = str
                    return@execute name
                }

                name = getUIString(StringIds.eStrUnknown)
                return@execute name
            } ?: ""
        }

        fun pairFormatLandmarkDetails(
            landmark: Landmark?, excludeCountry: Boolean = false
        ): Pair<String, String> {
            return GEMSdkCall.execute {
                landmark ?: return@execute Pair("", "")

                var formattedDescription: String
                val landmarkAddress = landmark.getAddress()

                var formattedName = formatName(landmark)
                formattedName.trim()

                val pos = formattedName.indexOf("<")
                val pos1 = formattedName.indexOf("<<")

                val include = ArrayList<Int>()
                if ((pos == 0) && (pos1 < 0)) {
                    include.add(EAddressField.EStreetName.value)
                    include.add(EAddressField.EStreetNumber.value)
                    formattedName = landmarkAddress?.format(ArrayList(), include) ?: ""
                    include.clear()
                }

                var fieldValue = landmarkAddress?.getField(EAddressField.EStreetName) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.EStreetName.value)
                    include.add(EAddressField.EStreetNumber.value)
                }

                fieldValue = landmarkAddress?.getField(EAddressField.ESettlement) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.ESettlement.value)
                }

                fieldValue = landmarkAddress?.getField(EAddressField.ECity) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.ECity.value)
                }

                fieldValue = landmarkAddress?.getField(EAddressField.EDistrict) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.EDistrict.value)
                }

                fieldValue = landmarkAddress?.getField(EAddressField.ECounty) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.ECounty.value)
                }

                fieldValue = landmarkAddress?.getField(EAddressField.EState) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.EState.value)
                }

                if (!excludeCountry) {
                    fieldValue = landmarkAddress?.getField(EAddressField.ECountry) ?: ""
                    if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                        include.add(EAddressField.ECountry.value)
                    }
                }

                formattedDescription = landmarkAddress?.format(ArrayList(), include) ?: ""
                formattedName.trim()
                formattedDescription.trim()

                if (formattedDescription.indexOf(", ") == 0) {
                    formattedDescription.removeRange(0, 2)
                }

                val commaPos = formattedDescription.indexOf(", ")
                if ((commaPos > 0) && (commaPos == formattedDescription.length - 2)) {
                    formattedDescription.removeRange(
                        formattedDescription.length - 2,
                        formattedDescription.length
                    )
                }

                val settlement = landmarkAddress?.getField(EAddressField.ESettlement) ?: ""
                val city = landmarkAddress?.getField(EAddressField.ECity) ?: ""
                if (settlement.isNotEmpty() && city.isNotEmpty() && (settlement == city)) {
                    val posL = formattedDescription.indexOf(settlement)
                    if (posL == 0) {
                        var fragment = formattedDescription.substring(settlement.length)
                        if (fragment.indexOf(settlement) >= 0) {
                            if (fragment.indexOf(", ") == 0) {
                                fragment = fragment.removeRange(0, 2)
                            }
                            formattedDescription = fragment
                        }
                    } else if (posL > 0) {
                        val fragment1 = formattedDescription.substring(0, posL)
                        val fragment2 = formattedDescription.substring(posL + settlement.length)
                        if (fragment2.indexOf(settlement) > 0) {
                            if (fragment2.indexOf(", ") == 0) {
                                fragment2.removeRange(0, 2)
                            }
                            formattedDescription = fragment1 + fragment2
                        }
                    }
                }

                val landmarkDescrpition = landmark.getDescription() ?: ""

                return@execute if (formattedDescription.isEmpty() && landmarkDescrpition.isNotEmpty()) {
                    Pair(formattedName, landmarkDescrpition)
                } else {
                    Pair(formattedName, formattedDescription)
                }
            } ?: Pair("", "")
        }

        private const val MPS_TO_KMH = 3.6
        private const val MPS_TO_MPH = 2.237

        fun getSpeedText(speedInMPS: Double, unitsSystem: EUnitSystem): Pair<String, String> {
            val nSpeed: Int
            val speedUnitText: String

            if (unitsSystem == EUnitSystem.EMetric) {
                nSpeed = ((MPS_TO_KMH * speedInMPS) + 0.5).toInt()
                speedUnitText = getUIString(StringIds.eStrKmH)
            } else {
                nSpeed = ((MPS_TO_MPH * speedInMPS) + 0.5).toInt()
                speedUnitText = getUIString(StringIds.eStrMph)
            }

            return Pair(String.format("%d", nSpeed), speedUnitText)
        }

        fun getTimeTextWithDays(
            timeInSeconds: Int,
            bCapitalizeResult: Boolean = false,
            bNoLessThanOneMinute: Boolean = false,
            bMinutesOnly: Boolean = false,
            bForceDays: Boolean = false,
            bForceHours: Boolean = false,
            bForceMin: Boolean = false
        ): Pair<String, String> {
            val totalInMinutes = timeInSeconds / 60
            val totalInHours = totalInMinutes / 60
            val totalInDays = totalInHours / 24
            val totalInYears = totalInDays / 365
            val nDecades = totalInYears / 10
            val nYears = totalInYears - (nDecades * 10)
            val nDays = totalInDays - (nYears * 365)
            val nHour = totalInHours - (nDays * 24)
            val nMin = totalInMinutes - (nHour * 60)
            var nSec = timeInSeconds % 60

            // units abbreviations
            val dAbbrev = "d"
            val hrAbbrev = getUIString(StringIds.eStrHoursAbbrev)
            val mAbbrev = getUIString(StringIds.eStrTimeMin)
            val sAbbrev = getUIString(StringIds.eStrTimeS)

            val time: String
            val unit: String

            if (bMinutesOnly) {
                time = String.format("%d", totalInMinutes)
                unit = mAbbrev
            } else {
                if ((nDays > 0) || bForceDays) {
                    unit = if (nDays > 99) {
                        time = String.format("%d+", nDays)
                        dAbbrev
                    } else {
                        time = String.format("%d:%d", nDays, nHour)
                        dAbbrev
                    }
                } else if ((nHour > 0) || bForceHours) {
                    time = String.format("%d:%02d", nHour, nMin)
                    unit = hrAbbrev
                } else if ((nMin > 0) || bForceMin) {
                    time = String.format("%d", nMin)
                    unit = mAbbrev
                } else {
                    nSec -= nSec % 5

                    unit = if (bNoLessThanOneMinute) {
                        time = String.format("%d", 1)
                        mAbbrev
                    } else {
                        time = String.format("%d", nSec)
                        sAbbrev
                    }
                }
            }

            if (bCapitalizeResult) {
                time.toUpperCase(Locale.getDefault())
                unit.toUpperCase(Locale.getDefault())
            }

            return Pair(time, unit)
        }
    }
}
