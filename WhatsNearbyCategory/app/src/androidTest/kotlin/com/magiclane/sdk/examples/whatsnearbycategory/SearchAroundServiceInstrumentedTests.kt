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

package com.magiclane.sdk.examples.whatsnearbycategory

// -------------------------------------------------------------------------------------------------
import android.Manifest
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.ext.junit.rules.ActivityScenarioRule
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
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import kotlin.system.measureTimeMillis

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SearchAroundServiceInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var mActivityIdlingResource: IdlingResource? = null

    private var appContext: Context = ApplicationProvider.getApplicationContext()


    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            mActivityIdlingResource = activity.getActivityIdlingResource()
            // To prove that the test fails, omit this call:
            IdlingRegistry.getInstance().register(mActivityIdlingResource)
        }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        if (mActivityIdlingResource != null)
            IdlingRegistry.getInstance().unregister(mActivityIdlingResource)
    }

    // -------------------------------------------------------------------------------------------------
    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    @Test
    fun searchAroundPositionShouldReturnListOfNearbyGasStations() = runBlocking {

        val channel = Channel<Unit>()
        var onCompletedPassed = false
        var res = LandmarkList()
        var error: ErrorCode
        val searchService = SearchService(
            onCompleted = { results, errorCode, _ ->
                onCompletedPassed = true
                res = results
                error = errorCode
                launch {
                    channel.send(Unit)
                }
            }
        )

        error = async {
            SdkCall.execute {
                val centerLondon = Coordinates(51.5072, 0.1276)
                searchService.searchAroundPosition(
                    EGenericCategoriesIDs.GasStation,
                    coords = centerLondon
                )
            }
        }.await() as ErrorCode


        val job = launch {
            //waits till a matching channel.send() is invoked
            channel.receive()
        }

        withTimeout(300000) {
            while (job.isActive) delay(1000)
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            assert(error == GemError.NoError)
            { "An error occurred on GEM SDK thread: ${GemError.getMessage(error)}" }
            assert(res.isNotEmpty())
            {
                "Result list is empty. This might be a fake error if the current" +
                        " location truly does not have ${EGenericCategoriesIDs.GasStation} around"
            }
        }
    }

    // -------------------------------------------------------------------------------------------------

    @Test
    fun searchAroundPositionShouldReturnListOfNearbyGasStationsFromCurrentPosition() = runBlocking {

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

        val job = launch {
            //waits till a matching channel.send() is invoked
            channel.receive()
        }

        error = async {
            SdkCall.execute {
                searchService.searchAroundPosition(EGenericCategoriesIDs.GasStation)
            }
        }.await() as ErrorCode

        withTimeout(300000) {
            while (job.isActive) delay(1000)
            //checks weather search around position called onCompleted
            assert(onCompletedPassed) { "OnCompleted not passed : ${GemError.getMessage(error)}" }
            //checks weather response was an error
            assert(error == GemError.NoError)
            { "An error occurred on GEM SDK thread: ${GemError.getMessage(error)}" }
            assert(res.isNotEmpty())
            {
                "Result list is empty. This might be a fake error if the current" +
                        " location truly does not have ${EGenericCategoriesIDs.GasStation.name} around"
            }
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    /** not a test*/
    private fun isLocationEnabled(): Boolean
    {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }
}