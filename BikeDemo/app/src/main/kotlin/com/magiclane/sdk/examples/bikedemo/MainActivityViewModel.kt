/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemo

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.core.EVoiceType
import com.magiclane.sdk.core.GenericCategories
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.routesandnavigation.ERouteTransportMode
import com.magiclane.sdk.routesandnavigation.ElectricBikeProfile
import com.magiclane.sdk.routesandnavigation.RoutePreferences
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivityViewModel : ViewModel() {

    companion object {
        // Language set on the TTS engine (see MainActivity.onTTSPlayerInitialized).
        const val TTS_LANGUAGE = "eng-USA"

        // Name of the human voice bundled with the SDK, used as fallback when the
        // TTS engine could not be initialized and no voice has been applied yet.
        const val DEFAULT_HUMAN_VOICE_NAME = "Michael"
    }

    /** Snapshot of the voice currently applied to the SDK. */
    data class VoiceSelection(
        val isTts: Boolean,
        val name: String,
        val filename: String,
    )

    val searchResultListLivedata = MutableLiveData<MutableList<SearchResultItem>>()
    val isElectricBikeProfile = MutableLiveData(false)

    // POI categories shown in the horizontal bar, and the index of the selected chip.
    val categoriesLivedata = MutableLiveData<List<CategoryItem>>(emptyList())
    val selectedCategory = MutableLiveData(CategoryAdapter.NO_CATEGORY)
    private var categoriesLoaded = false

    // Loads the generic POI categories once the SDK map data is ready.
    fun loadCategories(iconSize: Int) {
        if (categoriesLoaded) return
        categoriesLoaded = true
        viewModelScope.launch(Dispatchers.IO) {
            val items = SdkCall.execute {
                GenericCategories().categories?.mapNotNull { cat ->
                    CategoryItem(
                        name = cat.name ?: return@mapNotNull null,
                        icon = cat.image?.asBitmap(iconSize, iconSize),
                        landmarkStoreId = cat.landmarkStoreId,
                        categoryId = cat.id,
                    )
                } ?: emptyList()
            } ?: emptyList()
            categoriesLivedata.postValue(items)
        }
    }

    // Voice used for navigation / simulation instructions. The selection itself is held by the
    // SDK (SdkSettings.voice); this LiveData mirrors it so the UI can react to changes.
    val currentVoice = MutableLiveData<VoiceSelection?>(null)

    // Human voices catalog from the content store; null until the first successful fetch.
    val voicesLivedata = MutableLiveData<List<ContentStoreItem>?>(null)
    val contentStore = ContentStore()

    fun refreshCurrentVoice() {
        SdkCall.execute {
            val voice = SdkSettings.voice
            val selection = when (voice?.type) {
                EVoiceType.Computer -> VoiceSelection(isTts = true, name = "", filename = voice.filename ?: "")
                EVoiceType.Human -> VoiceSelection(
                    isTts = false,
                    name = voice.name?.takeIf { it.isNotEmpty() } ?: DEFAULT_HUMAN_VOICE_NAME,
                    filename = voice.filename ?: "",
                )
                // No voice applied yet: reflect what MainActivity will apply once the
                // TTS engine initialization outcome is known.
                else -> VoiceSelection(
                    isTts = SoundPlayingService.ttsPlayerIsInitialized,
                    name = DEFAULT_HUMAN_VOICE_NAME,
                    filename = "",
                )
            }
            currentVoice.postValue(selection)
        }
    }

    fun selectTtsVoice() {
        SoundPlayingService.setTTSLanguage(TTS_LANGUAGE)
        refreshCurrentVoice()
    }

    fun selectHumanVoice(item: ContentStoreItem) {
        SdkCall.execute {
            item.fileName?.takeIf { it.isNotEmpty() }?.let { SdkSettings.setVoiceByPath(it) }
        }
        refreshCurrentVoice()
    }

    var destination: Landmark? = null
    var isElectric = false
    var bikeProfile = EBikeProfile.City
    lateinit var routePreferences: RoutePreferences
    private var electricBikeProfile: ElectricBikeProfile? = null
    private var hillsFactor = 5f
    private var bikeWeight = 12f
    private var bikerWeight = 70f
    private var auxConsumptionDay = 20f
    private var auxConsumptionNight = 20f
    private var avoidFerries = false
    private var avoidUnpavedRoads = false

    fun initPreferences() {
        electricBikeProfile = ElectricBikeProfile()
        routePreferences = RoutePreferences().apply {
            transportMode = ERouteTransportMode.Bicycle
            setBikeProfile(EBikeProfile.City, if (isElectric) electricBikeProfile else null)
            hillsFactor
        }
    }

    fun setBikeProfile(type: EBikeProfile) = SdkCall.execute {
        bikeProfile = type
        routePreferences.setBikeProfile(type, if (isElectric) electricBikeProfile else null)
    }

    private fun setIsElectric(isElectric: Boolean) = SdkCall.execute {
        this.isElectric = isElectric
        isElectricBikeProfile.postValue(isElectric)
        setBikeProfile(bikeProfile)
    }

    fun getSettingsList(voiceValue: String, onVoiceClicked: () -> Unit): MutableList<SettingsItem> {
        // Build a fresh list on every call: ListAdapter.submitList ignores a list with the
        // same reference, and the Voice value can change between two calls.
        val settingsList = mutableListOf<SettingsItem>()
        settingsList.apply {
            add(
                SettingsTextItem("Voice", voiceValue) {
                    onVoiceClicked()
                },
            )
            add(
                SettingsSwitchItem("E-Bike", isElectric) {
                    setIsElectric(it)
                },
            )
            add(
                SettingsSliderItem("Hills", 0f, hillsFactor, 10f, "") {
                    hillsFactor = it
                    SdkCall.execute { routePreferences.avoidBikingHillFactor = it }
                },
            )
            add(
                SettingsSwitchItem("Avoid Ferries", avoidFerries) {
                    avoidFerries = it
                    SdkCall.execute { routePreferences.avoidFerries = it }
                },
            )
            add(
                SettingsSwitchItem("Avoid Unpaved Roads", avoidUnpavedRoads) {
                    avoidUnpavedRoads = it
                    SdkCall.execute { routePreferences.avoidUnpavedRoads = it }
                },
            )
            add(
                SettingsSliderItem("Bike Weight", 9f, bikeWeight, 50f, "kg") {
                    bikerWeight = it
                    SdkCall.execute { electricBikeProfile?.bikeMass = it }
                },
            )
            add(
                SettingsSliderItem("Biker Weight", 10f, bikerWeight, 150f, "kg") {
                    bikerWeight = it
                    SdkCall.execute { electricBikeProfile?.bikerMass = it }
                },
            )
            add(
                SettingsSliderItem("Aux Consumption Day", 0f, auxConsumptionDay, 100f, "Wh/h") {
                    auxConsumptionDay = it
                    SdkCall.execute { electricBikeProfile?.auxConsumptionDay = it }
                },
            )
            add(
                SettingsSliderItem("Aux Consumption Night", 0f, auxConsumptionNight, 100f, "Wh/h") {
                    auxConsumptionNight = it
                    SdkCall.execute { electricBikeProfile?.auxConsumptionNight = it }
                },
            )
        }
        return settingsList
    }
}
