/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.androidauto.services

import com.magiclane.sdk.core.SettingsService

object SettingsInstance {
    lateinit var service: SettingsService
        private set

    fun isInitialized(): Boolean = this::service.isInitialized

    fun init() {
        if (isInitialized())
            return

        SettingsService.produce("Settings.ini")?.let { service = it }
    }
}