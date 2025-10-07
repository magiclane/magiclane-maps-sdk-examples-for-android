// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.rangefinder

import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OBS: Depending on the device used and because this is a large test suite
 * some tests may be flaky tests due to espresso framework limitations
 */
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class RangeFinderInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: MainActivity
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerIdlingResource(): Unit = runBlocking {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        delay(2000)
        activityScenarioRule.scenario.onActivity { activity ->
            IdlingRegistry.getInstance().register(MainActivity.EspressoIdlingResource.espressoIdlingResource)
            activityRes = activity
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        IdlingRegistry.getInstance().unregister(MainActivity.EspressoIdlingResource.espressoIdlingResource)
    }

    @Test
    fun createRange(): Unit = runBlocking {

        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())
        delay(2000)
        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) > 0
            } ?: false
        }.await()
        assert(res)
    }

    @Test
    fun createAndHideRange(): Unit = runBlocking {
        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())
        runBlocking { delay(2000) }
        onView(withSubstring("300")).perform(click())
        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) <= 0
            } ?: false
        }.await()

        assert(res)
    }

    @Test
    fun createMaximumAmountOfRangesAndCheckWarningDialogShows(): Unit = runBlocking {
        for (index in 0..MAX_ITEMS)
        {
            onView(withId(R.id.range_value_edit_text)).perform(typeText((300 + index * 10).toString()))
            onView(withId(R.id.add_button)).perform(click())
            if (index == MAX_ITEMS)
            {
                val txt = activityRes.resources.getString(R.string.maximum_items_warning, MAX_ITEMS)
                onView(withSubstring(txt)).check(matches(isDisplayed()))
                continue
            }
            delay(4000)
        }

        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) == MAX_ITEMS
            } ?: false
        }.await()

        assert(res)
    }

    @Test
    fun createRangesOfDifferentTransportTypesForFastestRangeType(): Unit = runBlocking {

        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())
        runBlocking { delay(6000) }

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.lorry))).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("301"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.pedestrian))).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("302"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.bicycle))).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("303"))
        onView(withId(R.id.add_button)).perform(click())

        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) == 4
            } ?: false
        }.await()

        assert(res)
    }

    @Test
    fun createRangesOfDifferentTransportTypesForShortestRangeType(): Unit = runBlocking {

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.range_type_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.shortest))).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())
        delay(6000)

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.lorry))).perform(click())
        onView(withId(R.id.range_type_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.shortest))).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("301"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.pedestrian))).perform(click())
        onView(withId(R.id.range_type_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.shortest))).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("302"))
        onView(withId(R.id.add_button)).perform(click())


        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) == 3
            } ?: false
        }.await()

        assert(res)
    }

    @Test
    fun createBicycleRangesForFastestType(): Unit = runBlocking {

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.bicycle))).perform(click())
        onView(withId(R.id.range_type_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.fastest))).perform(click())

        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.City.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.Road.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.Cross.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.Mountain.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("300"))
        onView(withId(R.id.add_button)).perform(click())

        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) == 4
            } ?: false
        }.await()

        assert(res)
    }

    @Test
    fun createBicycleRangesForEconomicType(): Unit = runBlocking {

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.transport_mode_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.bicycle))).perform(click())
        onView(withId(R.id.range_type_selector)).perform(click())
        onView(withSubstring(activityRes.resources.getString(R.string.economic))).perform(click())
        onView(withId(R.id.bike_weight_edit_text)).perform(typeText("12"))
        onView(withId(R.id.biker_weight_edit_text)).perform(typeText("65"))

        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.City.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("30"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.Road.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("30"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.Cross.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("30"))
        onView(withId(R.id.add_button)).perform(click())

        onView(withId(R.id.options_button)).perform(click())
        onView(withId(R.id.bike_type_selector)).perform(click())
        onView(withSubstring(EBikeProfile.Mountain.name)).perform(click())
        onView(withId(R.id.range_value_edit_text)).perform(typeText("30"))
        onView(withId(R.id.add_button)).perform(click())

        val res = async {
            SdkCall.execute {
                (activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view).mapView?.preferences?.routes?.size
                    ?: 0) == 4
            } ?: false
        }.await()

        assert(res)
    }


    @Test
    fun checkRotation():Unit = runBlocking {
        activityRes.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        delay(5000)
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
    }
}
