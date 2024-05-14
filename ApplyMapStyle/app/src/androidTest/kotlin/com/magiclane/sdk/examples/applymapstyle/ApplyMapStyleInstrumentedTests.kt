
/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.applymapstyle

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
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
class ApplyMapStyleInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)


    private lateinit var activityRes: MainActivity
    private var idlingResource: IdlingResource? = null
    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
            activityRes = activity
        }
    }

    @After
    fun closeActivity()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test 
    fun checkMapStatusView() : Unit = runBlocking{
        onView(withId(R.id.status_text)).check(matches(isDisplayed()))
        onView(withId(R.id.status_text)).check(matches(withSubstring("Style")))
    }
}
