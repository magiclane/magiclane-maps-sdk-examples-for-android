/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.demo.util

import com.generalmagic.sdk.core.*
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.places.AddressInfo
import com.generalmagic.sdk.places.EAddressField
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.RouteTrafficEvent
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.SdkUtil
import com.generalmagic.sdk.util.SdkUtil.getDistText
import com.generalmagic.sdk.util.SdkUtil.getTimeText
import com.generalmagic.sdk.util.StringIds
import java.util.*

object UtilUITexts {
    fun getUIString(stringIds: StringIds): String = SdkCall.execute {
        SdkUtil.getUIString(stringIds)
    } ?: ""

    fun formatTrafficDelayAndLength(length: Int, delay: Int, isRoadblock: Boolean): String {
        val distText = getDistText(length, SdkSettings().unitSystem)

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
            event?.length ?: 0,
            event?.delay ?: 0,
            event?.isRoadblock() ?: false
        )
    }

    fun formatSizeAsText(inSizeInBytes: Long): String {
        var sizeInBytes: Double = inSizeInBytes.toDouble()
        val unit: String

        when {
            sizeInBytes < (1024.0 * 1024.0) -> {
                sizeInBytes /= 1024.0
                unit = getUIString(StringIds.eStrKiloByte)
            }
            sizeInBytes < (1024.0 * 1024.0 * 1024.0) -> {
                sizeInBytes /= (1024.0 * 1024.0)
                unit = getUIString(StringIds.eStrMegaByte)
            }
            else -> {
                sizeInBytes /= (1024.0 * 1024.0 * 1024.0)
                unit = getUIString(StringIds.eStrGigaByte)
            }
        }

        var size = String.format("%.2f", sizeInBytes)

        val pos = size.indexOf(".00")
        if ((pos > 0) && (pos == (size.length - 3))) {
            size = size.substring(0, pos)
        }

        return String.format("%s %s", size, unit)
    }

    fun getFormattedDistanceTime(distanceInMeters: Int, timeInSeconds: Int): String {
        val dist = getDistText(distanceInMeters, SdkSettings().unitSystem, true)

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
        SdkCall.execute {
            val landmarkAddress = landmark.addressInfo ?: AddressInfo()

            val street = landmarkAddress.getField(EAddressField.StreetName) ?: ""
            val city = landmarkAddress.getField(EAddressField.City) ?: ""
            val settlement = landmarkAddress.getField(EAddressField.Settlement) ?: ""
            val county = landmarkAddress.getField(EAddressField.County) ?: ""

            if (bFillAllFields || (city.isEmpty() && settlement.isEmpty() && county.isEmpty())) {
                var landmarkList = ArrayList<Landmark>()
                val coords = landmark.coordinates
                if (coords != null) {
                    val mainView = GEMApplication.getMainMapView()
                    landmarkList = mainView?.getNearestLocations(coords) ?: landmarkList
                }

                if (landmarkList.isNotEmpty()) {
                    val lmk = landmarkList[0]
                    val lmkAddress = lmk.addressInfo

                    val aStreet = lmkAddress?.getField(EAddressField.StreetName) ?: ""
                    val aCity = lmkAddress?.getField(EAddressField.City) ?: ""
                    val aSettlement = lmkAddress?.getField(EAddressField.Settlement) ?: ""
                    val aCounty = lmkAddress?.getField(EAddressField.County) ?: ""

                    if (street.isEmpty() && aStreet.isNotEmpty()) {
                        landmarkAddress.setField(aStreet, EAddressField.StreetName)
                    }

                    if (city.isEmpty() && aCity.isNotEmpty()) {
                        landmarkAddress.setField(aCity, EAddressField.City)
                    }

                    if (settlement.isEmpty() && aSettlement.isNotEmpty()) {
                        landmarkAddress.setField(aSettlement, EAddressField.Settlement)
                    }

                    if (county.isEmpty() && aCounty.isNotEmpty()) {
                        landmarkAddress.setField(aCounty, EAddressField.County)
                    }

                    if (bFillAllFields) {
                        val aCountry = lmkAddress?.getField(EAddressField.Country) ?: ""
                        val aCountryCode =
                            lmkAddress?.getField(EAddressField.CountryCode) ?: ""
                        val country = landmarkAddress.getField(EAddressField.Country) ?: ""
                        val countryCode =
                            landmarkAddress.getField(EAddressField.CountryCode) ?: ""

                        if (country.isEmpty() && aCountry.isNotEmpty()) {
                            landmarkAddress.setField(aCountry, EAddressField.Country)
                        }

                        if (countryCode.isEmpty() && aCountryCode.isNotEmpty()) {
                            landmarkAddress.setField(aCountryCode, EAddressField.CountryCode)
                        }
                    }
                }
            }
        }
    }

    fun formatLandmarkDetails(landmark: Landmark, excludeCountry: Boolean = false): String {
        return SdkCall.execute {
            val formatted = pairFormatLandmarkDetails(landmark, excludeCountry)

            return@execute if (formatted.second.isNotEmpty()) {
                String.format("%s, %s", formatted.first, formatted.second)
            } else {
                formatted.first
            }
        } ?: ""
    }

    fun getLandmarkDescription(landmark: Landmark, excludeCountry: Boolean = false): String {
        return SdkCall.execute {
            val formatted = pairFormatLandmarkDetails(landmark, excludeCountry)

            return@execute if (formatted.second.isNotEmpty()) {
                formatted.second
            } else {
                formatted.first
            }
        } ?: ""
    }

    fun formatName(landmark: Landmark): String {
        return SdkCall.execute {
            var name = "" // result

            var str = landmark.name ?: ""

            if (str.isNotEmpty()) {
                name = str
                return@execute name
            }

            val landmarkAddress = landmark.addressInfo
            str = landmarkAddress?.getField(EAddressField.StreetName) ?: ""

            if (str.isNotEmpty()) {
                val includeList = arrayListOf(
                    EAddressField.StreetName.value,
                    EAddressField.StreetNumber.value
                )

                name = landmarkAddress?.format(ArrayList(), includeList) ?: name
                return@execute name
            }

            str = landmarkAddress?.getField(EAddressField.City) ?: ""
            if (str.isNotEmpty()) {
                name = str
                val image = landmark.image ?: return@execute name
                if (image.uid == SdkImages.Engine_Misc.LocationDetails_SendDetails.value.toLong()) {
                    val postalCode = landmarkAddress?.getField(EAddressField.PostalCode) ?: ""

                    if (postalCode.isNotEmpty()) {
                        name += " "
                        name += landmarkAddress?.getField(EAddressField.PostalCode)
                    }
                }
                return@execute name
            }

            str = landmarkAddress?.getField(EAddressField.Settlement) ?: ""
            if (str.isNotEmpty()) {
                name = str
                return@execute name
            }

            str = landmarkAddress?.getField(EAddressField.County) ?: ""
            if (str.isNotEmpty()) {
                name = str
                return@execute name
            }

            str = landmarkAddress?.getField(EAddressField.State) ?: ""
            if (str.isNotEmpty()) {
                name = str
                return@execute name
            }

            str = landmarkAddress?.getField(EAddressField.Country) ?: ""
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
        return SdkCall.execute {
            landmark ?: return@execute Pair("", "")

            var formattedDescription: String
            val landmarkAddress = landmark.addressInfo

            var formattedName = formatName(landmark)
            formattedName.trim()

            val pos = formattedName.indexOf("<")
            val pos1 = formattedName.indexOf("<<")

            val include = ArrayList<Int>()
            if ((pos == 0) && (pos1 < 0)) {
                include.add(EAddressField.StreetName.value)
                include.add(EAddressField.StreetNumber.value)
                formattedName = landmarkAddress?.format(ArrayList(), include) ?: ""
                include.clear()
            }

            var fieldValue = landmarkAddress?.getField(EAddressField.StreetName) ?: ""
            if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                include.add(EAddressField.StreetName.value)
                include.add(EAddressField.StreetNumber.value)
            }

            fieldValue = landmarkAddress?.getField(EAddressField.Settlement) ?: ""
            if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                include.add(EAddressField.Settlement.value)
            }

            fieldValue = landmarkAddress?.getField(EAddressField.City) ?: ""
            if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                include.add(EAddressField.City.value)
            }

            fieldValue = landmarkAddress?.getField(EAddressField.District) ?: ""
            if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                include.add(EAddressField.District.value)
            }

            fieldValue = landmarkAddress?.getField(EAddressField.County) ?: ""
            if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                include.add(EAddressField.County.value)
            }

            fieldValue = landmarkAddress?.getField(EAddressField.State) ?: ""
            if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                include.add(EAddressField.State.value)
            }

            if (!excludeCountry) {
                fieldValue = landmarkAddress?.getField(EAddressField.Country) ?: ""
                if ((fieldValue.isNotEmpty()) && (formattedName.indexOf(fieldValue) < 0)) {
                    include.add(EAddressField.Country.value)
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

            val settlement = landmarkAddress?.getField(EAddressField.Settlement) ?: ""
            val city = landmarkAddress?.getField(EAddressField.City) ?: ""
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

            val landmarkDescrpition = landmark.description ?: ""

            return@execute if (formattedDescription.isEmpty() && landmarkDescrpition.isNotEmpty()) {
                Pair(formattedName, landmarkDescrpition)
            } else {
                Pair(formattedName, formattedDescription)
            }
        } ?: Pair("", "")
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
        val hrAbbrev = SdkCall.execute { getUIString(StringIds.eStrHoursAbbrev) } ?: ""
        val mAbbrev = SdkCall.execute { getUIString(StringIds.eStrTimeMin) } ?: ""
        val sAbbrev = SdkCall.execute { getUIString(StringIds.eStrTimeS) } ?: ""

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
            time.uppercase(Locale.getDefault())
            unit.uppercase(Locale.getDefault())
        }

        return Pair(time, unit)
    }
}

