// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.mapgestures

import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.slowSwipeLeft
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MapGesturesInstrumentedTests
{

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: MainActivity

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun touchEvent()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        var touched = false
        activityRes.gemSurfaceView.mapView?.onTouch = {
            touched = true
        }
        onView(withId(R.id.gem_surface)).perform(click())
        runBlocking { delay(2000) }
        assert(touched)
    }

    @Test
    fun doubleTouchEvent()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        var touched = false
        activityRes.gemSurfaceView.mapView?.onDoubleTouch = {
            touched = true
        }
        onView(withId(R.id.gem_surface)).perform(click())
        onView(withId(R.id.gem_surface)).perform(click())
        runBlocking { delay(2000) }
        assert(touched)
    }

    @Test
    fun onLongDownEvent()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        var touched = false
        activityRes.gemSurfaceView.mapView?.onLongDown = {
            touched = true
        }
        onView(withId(R.id.gem_surface)).perform(longClick())
        runBlocking { delay(2000) }
        assert(touched)
    }

    @Test
    fun onSwipeEvent()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        var touched = false
        activityRes.gemSurfaceView.mapView?.onSwipe = { _, _, _ ->
            touched = true
        }
        onView(withId(R.id.gem_surface)).perform(swipeDown())
        runBlocking { delay(2000) }
        assert(touched)
    }

    @Test
    fun onMoveEvent()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        var touched = false
        activityRes.gemSurfaceView.mapView?.onMove = { _, _ ->
            touched = true
        }
        onView(withId(R.id.gem_surface)).perform(slowSwipeLeft())
        runBlocking { delay(2000) }
        assert(touched)
    }

    @Test
    fun onPinchEvent()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))

        var pinched = false
        var result: Boolean
        activityRes.gemSurfaceView.mapView?.onPinch = { _, _, _, _, _ ->
            pinched = true
        }

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            result =
                findObject(UiSelector().resourceId("com.magiclane.sdk.examples.mapgestures:id/gem_surface")).pinchIn(
                    80,
                    200
                )
        }
        runBlocking { delay(3000) }
        assert(pinched) { result }
    }

}
