/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.generalmagic.sdk.examples.services

import com.generalmagic.sdk.core.OnStarted
import com.generalmagic.sdk.core.ProgressListener
import com.generalmagic.sdk.core.SettingsService
import com.generalmagic.sdk.places.LandmarkList
import com.generalmagic.sdk.places.OnSearchCompleted
import com.generalmagic.sdk.places.SearchService


object SearchInstance {
    val service: SearchService = SearchService()
    val results: LandmarkList = arrayListOf()
    val listeners = mutableListOf<ProgressListener>()

    // ---------------------------------------------------------------------------------------------

    private val settingsService: SettingsService
        get() {
            if (!SettingsInstance.isInitialized())
                SettingsInstance.init()
            return SettingsInstance.service
        }

    private val onStarted: OnStarted = { hasProgress ->
        this.results.clear()

        listeners.onEach { it.notifyStart(hasProgress) }
    }

    private val onCompleted: OnSearchCompleted = { results, errorCode, hint ->
        this.results.addAll(results)

        listeners.onEach { it.notifyComplete(errorCode, hint) }
    }

    // ---------------------------------------------------------------------------------------------

    fun init() {
        service.onStarted = onStarted
        service.onCompleted = onCompleted

        loadSettings()
    }

    // ---------------------------------------------------------------------------------------------
    // Settings

    var searchAddressesEnabled: Boolean
        get() = service.preferences.searchAddressesEnabled
        set(value) {
            settingsService.setBooleanValue("searchAddressesEnabled", value)
            service.preferences.searchAddressesEnabled = value
        }

    var searchMapPOIsEnabled: Boolean
        get() = service.preferences.searchMapPOIsEnabled
        set(value) {
            settingsService.setBooleanValue("searchMapPOIsEnabled", value)
            service.preferences.searchMapPOIsEnabled = value
        }

    private fun loadSettings() {
        service.preferences.searchAddressesEnabled =
            settingsService.getBooleanValue("searchAddressesEnabled", false)
        service.preferences.searchMapPOIsEnabled =
            settingsService.getBooleanValue("searchMapPOIsEnabled", false)
    }
}