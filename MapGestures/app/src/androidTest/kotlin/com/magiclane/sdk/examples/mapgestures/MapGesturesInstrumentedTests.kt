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


package com.magiclane.sdk.examples.mapgestures

import android.health.connect.datatypes.Device
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
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
import com.magiclane.sdk.core.GemSurfaceView
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

    private var mActivityIdlingResource: IdlingResource? = null

    private lateinit var activityRes: MainActivity

    //lateinit var device: UiDevice


    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            mActivityIdlingResource = activity.getActivityIdlingResource()
            // To prove that the test fails, omit this call:
            IdlingRegistry.getInstance().register(mActivityIdlingResource)
            activityRes = activity
        }
        //device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        if (mActivityIdlingResource != null)
            IdlingRegistry.getInstance().unregister(mActivityIdlingResource)
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
        var result = false
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