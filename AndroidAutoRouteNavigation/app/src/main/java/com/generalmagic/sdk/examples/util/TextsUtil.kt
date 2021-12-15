/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.util

import android.annotation.SuppressLint
import com.generalmagic.sdk.d3scene.MapView
import com.generalmagic.sdk.places.AddressInfo
import com.generalmagic.sdk.places.EAddressField
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.SdkImages
import com.generalmagic.sdk.util.SdkUtil
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.StringIds.eStrUnknown
import com.generalmagic.sdk.util.StringIds.eStringNodeNearby
import com.generalmagic.sdk.util.StringIds.eStringUnnamedStreet
import java.util.ArrayList

internal object TextsUtil {
    fun getUIString(stringIds: StringIds): String = SdkCall.execute {
        SdkUtil.getUIString(stringIds)
    } ?: ""

    fun fillLandmarkAddressInfo(
        mainView: MapView?,
        landmark: Landmark,
        fillAllFields: Boolean = false
    ) {
        val landmarkAddress = landmark.addressInfo ?: AddressInfo()

        val street = landmarkAddress.getField(EAddressField.StreetName) ?: ""
        val city = landmarkAddress.getField(EAddressField.City) ?: ""
        val settlement = landmarkAddress.getField(EAddressField.Settlement) ?: ""
        val county = landmarkAddress.getField(EAddressField.County) ?: ""

        if (fillAllFields || (city.isEmpty() && settlement.isEmpty() && county.isEmpty())) {
            var landmarkList = ArrayList<Landmark>()
            val coords = landmark.coordinates
            if (coords != null) {
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

                if (fillAllFields) {
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

    fun getFormattedWaypointName(landmark: Landmark): String {
        var text = "" // result

        val landmarkAddress = landmark.addressInfo ?: return ""

        var name = landmark.name ?: ""
        val streetName = landmarkAddress.getField(EAddressField.StreetName) ?: ""
        val streetNumber = landmarkAddress.getField(EAddressField.StreetNumber) ?: ""
        val settlement = landmarkAddress.getField(EAddressField.Settlement) ?: ""
        val city = landmarkAddress.getField(EAddressField.City) ?: ""
        val county = landmarkAddress.getField(EAddressField.County) ?: ""
        val state = landmarkAddress.getField(EAddressField.State) ?: ""
        val nearCity = ""
        val nearSettlement = ""

        if (city.isNotEmpty()) {
            nearCity.format(SdkUtil.getUIString(eStringNodeNearby), city)
        }

        if (settlement.isNotEmpty()) {
            nearSettlement.format(SdkUtil.getUIString(eStringNodeNearby), settlement)
        }

        if (streetName.isNotEmpty() && name.isNotEmpty()) {
            if ((name == nearCity) || (name == nearSettlement)) {
                name = ""
            }
        }

        if (name.isNotEmpty()) {
            val pos = name.indexOf("; ")
            if (pos > 0) {
                val description = name.substring(pos + 2)
                if (description.isNotEmpty()) {
                    if (((settlement.isNotEmpty()) && (description.indexOf(settlement) >= 0)) ||
                        ((city.isNotEmpty()) && (description.indexOf(city) >= 0)) ||
                        ((county.isNotEmpty()) && (description.indexOf(county) >= 0)) ||
                        ((state.isNotEmpty()) && (description.indexOf(state) >= 0))
                    ) {
                        // waypoint name was already formatted
                        text = name
                        text.replace(";", ",")
                        return text
                    }
                }
            }
        }

        val unknownXYZ = "Unknown_xyz"
        val bNameEmpty = name.isEmpty()
        var bRestoreStreetName = false

        landmark.name = ""
        if ((streetName.isEmpty()) && !bNameEmpty) {
            landmarkAddress.setField(unknownXYZ, EAddressField.StreetName)
            bRestoreStreetName = true
        }

        val nameDescription = pairFormatLandmarkDetails(landmark, true)
        var landmarkName = nameDescription.first
        val landmarkDescription = nameDescription.second

        landmarkName.trim()
        landmarkDescription.trim()

        if (landmarkName == unknownXYZ) {
            landmarkName = ""
        }

        landmark.name = name
        if (bRestoreStreetName) {
            landmarkAddress.setField(streetName, EAddressField.StreetName)
        }

        var nameParts = listOf<String>()
        if (!bNameEmpty) {
            nameParts = name.split(";,")

            for (str in nameParts) {
                str.trim()
            }
        }

        @SuppressLint("Indentation")
        val bContainsStreetNumber =
            name.indexOf("<") == 0 && name.indexOf("<<") < 0 && name.indexOf(">") > 0 && name.indexOf(
                ">>"
            ) < 0 && name.indexOf(streetNumber) == 1

        val nameContainsText: (text: String) -> Boolean = {
            var result = false
            for (str in nameParts) {
                if (str == text) {
                    result = true
                    break
                }
            }

            result
        }

        val bContainsStreetName =
            nameContainsText(streetName) || (nameParts[0].indexOf(streetName) > 0 && bContainsStreetNumber && nameParts.isNotEmpty())

        val bContainsCityOrSettlementName =
            nameContainsText(city) || nameContainsText(settlement) || nameContainsText(nearCity) || nameContainsText(
                nearSettlement
            )

        val bContainsLandmarkDescription = nameContainsText(landmarkDescription)

        if (bNameEmpty || bContainsStreetName) {
            text = landmarkName
            if ((text.isNotEmpty()) && (landmarkDescription.isNotEmpty())) {
                text += ", $landmarkDescription"
            } else if (text.isEmpty()) {
                if (bNameEmpty) {
                    text = SdkUtil.getUIString(eStringUnnamedStreet)
                    if (landmarkDescription.isNotEmpty()) {
                        text += ", $landmarkDescription"
                    }
                } else {
                    text = name
                    if (!bContainsLandmarkDescription &&
                        (landmarkDescription.isNotEmpty()) &&
                        !bContainsCityOrSettlementName
                    ) {
                        text += ", $landmarkDescription"
                    }
                }
            }
        } else if (!bNameEmpty) {
            text = name
            if (!bContainsLandmarkDescription &&
                (landmarkDescription.isNotEmpty()) &&
                !bContainsCityOrSettlementName
            ) {
                text += ", $landmarkDescription"
            }
        }

        return text
    }

    fun formatLandmarkDetails(landmark: Landmark, excludeCountry: Boolean = false): String {
        val formatted = pairFormatLandmarkDetails(landmark, excludeCountry)

        return if (formatted.second.isNotEmpty()) {
            String.format("%s, %s", formatted.first, formatted.second)
        } else {
            formatted.first
        }
    }

    fun getLandmarkDescription(landmark: Landmark, excludeCountry: Boolean = false): String {
        val formatted = pairFormatLandmarkDetails(landmark, excludeCountry)

        return if (formatted.second.isNotEmpty()) {
            formatted.second
        } else {
            formatted.first
        }
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

                name = landmarkAddress?.format(java.util.ArrayList(), includeList) ?: name
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

    fun formatName_two(landmark: Landmark): String {
        var name = "" // result

        var str = landmark.name ?: ""

        if (str.isNotEmpty()) {
            name = str
            return name
        }

        val landmarkAddress = landmark.addressInfo
        str = landmarkAddress?.getField(EAddressField.StreetName) ?: ""

        if (str.isNotEmpty()) {
            val includeList = arrayListOf(
                EAddressField.StreetName.value,
                EAddressField.StreetNumber.value
            )

            name = landmarkAddress?.format(ArrayList(), includeList) ?: name
            return name
        }

        str = landmarkAddress?.getField(EAddressField.City) ?: ""
        if (str.isNotEmpty()) {
            name = str
            val image = landmark.image ?: return name
            if (image.uid == SdkImages.Engine_Misc.LocationDetails_SendDetails.value.toLong()) {
                val postalCode = landmarkAddress?.getField(EAddressField.PostalCode) ?: ""

                if (postalCode.isNotEmpty()) {
                    name += " "
                    name += landmarkAddress?.getField(EAddressField.PostalCode)
                }
            }
            return name
        }

        str = landmarkAddress?.getField(EAddressField.Settlement) ?: ""
        if (str.isNotEmpty()) {
            name = str
            return name
        }

        str = landmarkAddress?.getField(EAddressField.County) ?: ""
        if (str.isNotEmpty()) {
            name = str
            return name
        }

        str = landmarkAddress?.getField(EAddressField.State) ?: ""
        if (str.isNotEmpty()) {
            name = str
            return name
        }

        str = landmarkAddress?.getField(EAddressField.Country) ?: ""
        if (str.isNotEmpty()) {
            name = str
            return name
        }

        name = SdkUtil.getUIString(eStrUnknown)
        return name
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

            val include = java.util.ArrayList<Int>()
            if ((pos == 0) && (pos1 < 0)) {
                include.add(EAddressField.StreetName.value)
                include.add(EAddressField.StreetNumber.value)
                formattedName = landmarkAddress?.format(java.util.ArrayList(), include) ?: ""
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

            formattedDescription = landmarkAddress?.format(java.util.ArrayList(), include) ?: ""
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

            val landmarkDescription = landmark.description ?: ""

            return@execute if (formattedDescription.isEmpty() && landmarkDescription.isNotEmpty()) {
                Pair(formattedName, landmarkDescription)
            } else {
                Pair(formattedName, formattedDescription)
            }
        } ?: Pair("", "")
    }

    fun pairFormatLandmarkDetails_two(
        landmark: Landmark,
        excludeCountry: Boolean = false
    ): Pair<String, String> {
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

        return if (formattedDescription.isEmpty() && landmarkDescrpition.isNotEmpty()) {
            Pair(formattedName, landmarkDescrpition)
        } else {
            Pair(formattedName, formattedDescription)
        }
    }
}
