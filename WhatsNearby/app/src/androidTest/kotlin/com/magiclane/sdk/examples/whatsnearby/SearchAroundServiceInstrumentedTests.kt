/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.whatsnearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchAroundServiceInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    private var appContext: Context = ApplicationProvider.getApplicationContext()

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.FOREGROUND_SERVICE,
    )

    @Test
    fun searchAroundPositionShouldReturnListOfNearbyLocations() = runBlocking {
        val channel = Channel<Unit>() // acts like a lock
        var onCompletedPassed = false
        var res = LandmarkList()
        var error: ErrorCode
        val searchService = SearchService(
            onStarted = {},
            onCompleted = { results, errorCode, _ ->
                onCompletedPassed = true
                res = results
                error = errorCode
                launch {
                    channel.send(Unit)
                }
            },
        )
        // checks for location enabled
        assert(isLocationEnabled()) { "Location was not enabled." }
        // checks for location permission access
        assert(
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        ) {
            "Permission to ${Manifest.permission.ACCESS_FINE_LOCATION}" +
                " and  ${Manifest.permission.ACCESS_COARSE_LOCATION} denied"
        }

        error = async {
            SdkCall.execute {
                // Search around position using the provided search preferences and/ or filter.
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchAroundPosition(centerLondon)
            }
        }.await() as ErrorCode
        assert(error == GemError.NoError) {
            "An error occurred on GEM SDK thread: ${GemError.getMessage(error)}"
        }
        withTimeout(300000) {
            // waits till a matching channel.send() is invoked
            channel.receive()
            // checks weather search around position called onCompleted
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            // checks weather response was an error
            assert(res.isNotEmpty()) {
                "Result list is empty. This might be a fake error if the current" +
                    " location truly does not have ${EGenericCategoriesIDs.GasStation} around"
            }
        }
    }

    /** not a test*/
    private fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }
}
