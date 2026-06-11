/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.testing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Utility for location-related checks in instrumented tests. */
object LocationUtils {

    /** Checks if location services are enabled. */
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java)
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** Checks if fine or coarse location permission is granted. */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /** Asserts that location services are enabled. */
    fun assertLocationEnabled(context: Context) {
        if (!isLocationEnabled(context)) {
            throw AssertionError(
                """
                |
                |------------------------------------------------------------------
                |Location services not enabled.
                |Emulator: Extended Controls -> Location -> Enable GPS signal
                |Device: Enable location in system settings
                |------------------------------------------------------------------
                |
                """.trimMargin()
            )
        }
    }

    /** Asserts that location permission is granted. */
    fun assertLocationPermission(context: Context) {
        if (!hasLocationPermission(context)) {
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                "Use GrantPermissionRule.grant(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)"
            } else {
                "Declare permission in AndroidManifest.xml"
            }
            throw AssertionError(
                """
                |
                |------------------------------------------------------------------
                |Location permission not granted.
                |$hint
                |------------------------------------------------------------------
                |
                """.trimMargin()
            )
        }
    }

    /** Asserts both location enabled and permission granted. */
    fun assertLocationAvailable(context: Context) {
        assertLocationPermission(context)
        assertLocationEnabled(context)
    }
}
