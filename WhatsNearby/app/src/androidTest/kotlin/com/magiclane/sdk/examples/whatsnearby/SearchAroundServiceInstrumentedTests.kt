// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.whatsnearby

// -------------------------------------------------------------------------------------------------

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import androidx.core.location.LocationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.EGenericCategoriesIDs
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.LandmarkList
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement


// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchAroundServiceInstrumentedTests {
    // -------------------------------------------------------------------------------------------------
    companion object {
        // -------------------------------------------------------------------------------------------------
        const val TIMEOUT = 600000L
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var initResult = false

        @get:ClassRule
        @JvmStatic
        val sdkInitRule = SDKInitRule()

        @BeforeClass
        @JvmStatic
        fun checkSdkInitStartActivity() {
            assert(initResult) { "GEM SDK not initialized" }
        }
        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
        // -------------------------------------------------------------------------------------------------
    }

    @Before
    fun checkTokenAndNetwork(){
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(isInternetOn()) { " No internet connection." }
    }
    
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    class SDKInitRule : TestRule {
        override fun apply(base: Statement, description: Description) = SDKStatement(base)

        inner class SDKStatement(private val base: Statement) : Statement() {
            private val channel = Channel<Boolean>()

            init {
                SdkSettings.onMapDataReady = { isReady ->
                    if (isReady)
                        runBlocking {
                            channel.send(true)
                        }
                }
            }

            @Throws(Throwable::class)
            override fun evaluate() {
                //before tests are executed
                if (!GemSdk.isInitialized()) {
                    runBlocking {
                        initResult = GemSdk.initSdkWithDefaults(appContext)
                        // must wait for map data ready
                        withTimeoutOrNull(TIMEOUT) {
                            channel.receive()
                        } ?: if (isInternetOn()) assert(false) { "No internet." }
                        else assert(false) { "Unexpected error. SDK not initialised." }
                    }
                } else return

                if (!SdkSettings.isMapDataReady)
                    throw Error(GemError.getMessage(GemError.OperationTimeout))

                try {
                    base.evaluate() // This executes tests
                } finally {
                    GemSdk.release()
                }
            }
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    @Test
    fun searchAroundPositionShouldReturnListOfNearbyLocations() = runBlocking {

        val channel = Channel<Unit>() //acts like a lock
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
            }
        )
        //checks for location enabled
        assert(isLocationEnabled()) { "Location was not enabled." }
        //checks for location permission access
        assert(
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
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
        assert(error == GemError.NoError)
        { "An error occurred on GEM SDK thread: ${GemError.getMessage(error)}" }
        withTimeout(300000) {
            //waits till a matching channel.send() is invoked
            channel.receive()
            //checks weather search around position called onCompleted
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            //checks weather response was an error
            assert(res.isNotEmpty())
            {
                "Result list is empty. This might be a fake error if the current" +
                    " location truly does not have ${EGenericCategoriesIDs.GasStation} around"
            }
        }
    }
// -------------------------------------------------------------------------------------------------
// -------------------------------------------------------------------------------------------------
    /** not a test*/
    private fun isLocationEnabled(): Boolean {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }
}
