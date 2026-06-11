/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapgestures

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
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MapGesturesInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: MainActivity

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun touchEvent() {
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
    fun doubleTouchEvent() {
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
    fun onLongDownEvent() {
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
    fun onSwipeEvent() {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        var touched = false
        activityRes.gemSurfaceView.mapView?.onSwipe = { _, _, _ ->
            touched = true
        }
        // Perform multiple swipe attempts — a single swipeDown() may be
        // interpreted as a "move" rather than a "swipe/fling" on some
        // emulator configurations if the velocity threshold is not met.
        repeat(3) {
            if (touched) return@repeat
            onView(withId(R.id.gem_surface)).perform(swipeDown())
            runBlocking { delay(2000) }
        }
        assert(touched)
    }

    @Test
    fun onMoveEvent() {
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
    fun onPinchEvent() {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))

        var pinched = false
        var result: Boolean
        activityRes.gemSurfaceView.mapView?.onPinch = { _, _, _, _, _ ->
            pinched = true
        }

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            result =
                findObject(
                    UiSelector().resourceId(
                        "com.magiclane.sdk.examples.mapgestures:id/gem_surface",
                    ),
                ).pinchIn(
                    80,
                    200,
                )
        }
        runBlocking { delay(3000) }
        assert(pinched) { result }
    }
}
