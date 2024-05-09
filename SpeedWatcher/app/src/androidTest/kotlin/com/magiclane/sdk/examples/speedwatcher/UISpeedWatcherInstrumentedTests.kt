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

package com.magiclane.sdk.examples.speedwatcher

// -------------------------------------------------------------------------------------------------

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.slowSwipeLeft
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.hamcrest.core.Is.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


// -------------------------------------------------------------------------------------------------

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UISpeedWatcherInstrumentedTests
{
    // -------------------------------------------------------------------------------------------------
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var mActivityIdlingResource: IdlingResource? = null

    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            mActivityIdlingResource = activity.getActivityIdlingResource()
            IdlingRegistry.getInstance().register(mActivityIdlingResource)
        }
    }

    @After
    fun closeActivity()
    {
        if (mActivityIdlingResource != null)
            IdlingRegistry.getInstance().unregister(mActivityIdlingResource)
        activityScenarioRule.scenario.close()
    }

    // -------------------------------------------------------------------------------------------------

    @Test
    fun mapIsInNavModeWhenOpeningApp()
    {
        onView(allOf(withId(R.id.gem_surface), isDisplayed())).check(GemSurfaceViewAssertionHelper(`is`(true)))
    }

    @Test
    fun testPositionButton()
    {

        runBlocking { delay(5000) }
        onView(allOf(withId(R.id.gem_surface), isDisplayed())).perform(slowSwipeLeft())
        onView(allOf(withId(R.id.follow_cursor), isDisplayed())).perform(click())
        onView(allOf(withId(R.id.gem_surface), isDisplayed())).check(GemSurfaceViewAssertionHelper(`is`(true)))
    }
    // -------------------------------------------------------------------------------------------------

    /** not tests*/
    class GemSurfaceViewAssertionHelper(private val matcher: Matcher<Boolean>) : ViewAssertion
    {
        override fun check(view: View?, noViewFoundException: NoMatchingViewException?)
        {
            view?.let {

                if (view is GemSurfaceView)
                {
                    SdkCall.execute{
                        val cameraMoving = view.mapView?.isCameraMoving()
                        val isFollowingPosition = view.mapView?.isFollowingPosition()
                        assertThat(cameraMoving, matcher)
                        assertThat(isFollowingPosition, matcher)
                    }
                }
            } ?: noViewFoundException
        }
    }

}
// -------------------------------------------------------------------------------------------------
