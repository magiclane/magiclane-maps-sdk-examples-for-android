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


package com.magiclane.sdk.examples.mapcompass

import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MapCompassInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var mActivityIdlingResource: IdlingResource? = null

    private lateinit var activityRes: MainActivity


    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            //mActivityIdlingResource = activity.getActivityIdlingResource()
            // To prove that the test fails, omit this call:
            //IdlingRegistry.getInstance().register(mActivityIdlingResource)
            activityRes = activity
        }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        if (mActivityIdlingResource != null)
            IdlingRegistry.getInstance().unregister(mActivityIdlingResource)
    }


    @Test
    fun checkLiveRotationText()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        val disabledText = activityRes.resources.getString(R.string.live_heading_disabled)
        val enabledText = activityRes.resources.getString(R.string.live_heading_enabled)
        onView(withId(R.id.status_text)).check(matches(withText(disabledText)))
        onView(withId(R.id.btn_enable_live_heading)).perform(click())
        onView(withId(R.id.status_text)).check(matches(withText(enabledText)))
    }


    @Test
    fun checkCompassRotation()
    {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_enable_live_heading)).perform(click())
        runBlocking { delay(5000) }
        assert(activityRes.findViewById<ImageView>(R.id.compass).rotation != 0f) { "This can also fail if device is facing towards north" }
    }
}