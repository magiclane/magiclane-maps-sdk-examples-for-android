/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.services

import com.magiclane.sdk.core.OnStarted
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SettingsService
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.OnSearchCompleted
import com.magiclane.sdk.places.SearchService


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