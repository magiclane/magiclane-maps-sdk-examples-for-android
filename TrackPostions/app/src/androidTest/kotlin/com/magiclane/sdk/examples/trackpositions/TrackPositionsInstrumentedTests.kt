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


package com.magiclane.sdk.examples.trackpositions
// -------------------------------------------------------------------------------------------------

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.trackpositions.MainActivity.Companion.paths
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Files

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class TrackPositionsInstrumentedTests()
{
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    companion object
    {
        // -------------------------------------------------------------------------------------------------
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        fun isInternetOn() = appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null
        // -------------------------------------------------------------------------------------------------
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun checkTokenAndNetwork()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(isInternetOn()) { " No internet connection." }
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
    }

    // -------------------------------------------------------------------------------------------------

    @After
    fun unregisterRes()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.onActivity { activity ->
            activity.finish()
        }
        activityScenarioRule.scenario.close()
    }

    // -------------------------------------------------------------------------------------------------

    @Test
    fun trackingPositionForGPXSavesFile(): Unit = runBlocking {
        val name = "path" + (paths.size + 1).toString() + ".gpx"
        deleteFile(name)
        delay(2000)
        onView(withId(R.id.start_stop)).check(matches(isDisplayed()))
        onView(withId(R.id.start_stop)).perform(click())
        delay(10000)
        onView(withId(R.id.start_stop)).perform(click())
        delay(1000)
        assert(checkFileExists(name)) { "Gpx file not saved." }
        deleteFile(name)
        delay(1000)
    }
    @Test
    fun trackingPositionForGPXSavesFile2(): Unit = runBlocking {
        val name = "path" +  (paths.size + 1).toString() + ".gpx"
        deleteFile(name)
        delay(2000)
        onView(withText(".gpx")).check(matches(isDisplayed()))
        onView(withText(".gpx")).perform(click())
        onView(withId(R.id.start_stop)).check(matches(isDisplayed()))
        onView(withId(R.id.start_stop)).perform(click())
        delay(10000)
        onView(withId(R.id.start_stop)).perform(click())
        delay(1000)
        assert(checkFileExists(name)) { "Gpx file not saved." }
        deleteFile(name)
        delay(1000)
    }

    @Test
    fun trackingPositionForKml(): Unit = runBlocking {
        val name = "path" +  (paths.size + 1).toString() + ".kml"
        deleteFile(name)
        delay(2000)
        onView(withText(".kml")).check(matches(isDisplayed()))
        onView(withText(".kml")).perform(click())
        onView(withId(R.id.start_stop)).check(matches(isDisplayed()))
        onView(withId(R.id.start_stop)).perform(click())
        delay(10000)
        onView(withId(R.id.start_stop)).perform(click())
        delay(1000)
        assert(checkFileExists(name)) { "Kml file not saved." }
        deleteFile(name)
        delay(1000)
    }

    @Test
    fun trackingPositionForJson(): Unit = runBlocking {
        val name = "path" +  (paths.size + 1).toString() + ".json"
        deleteFile(name)
        delay(2000)
        onView(withText(".json")).check(matches(isDisplayed()))
        onView(withText(".json")).perform(click())
        onView(withId(R.id.start_stop)).check(matches(isDisplayed()))
        onView(withId(R.id.start_stop)).perform(click())
        delay(10000)
        onView(withId(R.id.start_stop)).perform(click())
        delay(1000)
        assert(checkFileExists(name)) { "json file not saved." }
        deleteFile(name)
        delay(1000)
    }
    @Test
    fun trackingPositionForLatLonTxt(): Unit = runBlocking {
        val name = "path" +  (paths.size + 1).toString() + ".txt"
        deleteFile(name)
        delay(2000)
        onView(withText("Lat Lon text")).check(matches(isDisplayed()))
        onView(withText("Lat Lon text")).perform(click())
        onView(withId(R.id.start_stop)).check(matches(isDisplayed()))
        onView(withId(R.id.start_stop)).perform(click())
        delay(10000)
        onView(withId(R.id.start_stop)).perform(click())
        delay(1000)
        assert(checkFileExists(name)) { "Text file not saved." }
        deleteFile(name)
        delay(1000)
    }
    private fun checkFileExists(fileName: String): Boolean = File(GemSdk.internalStoragePath + File.separator + "exported" + File.separator + fileName).exists()
    private fun deleteFile(fileName: String) =  File(GemSdk.internalStoragePath + File.separator + "exported" + File.separator + fileName).apply { if (exists()) delete() }
    // -------------------------------------------------------------------------------------------------
}
