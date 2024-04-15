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


package com.magiclane.sdk.examples.multiplesurfacesinfragment

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
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
class MultipleSurfacesInFragmentRecyclerInstrumentationTests
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
            // mActivityIdlingResource = activity.getActivityIdlingResource()
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
    fun addMapTest()
    {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.bottom_right_button)).perform(click())
        assert(activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount == 2){
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount
        }
    }


    @Test
    fun removeMapTest()
    {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.bottom_left_button)).perform(click())
        assert(activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount == 0){
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount
        }
    }

}