/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.externalpositionsourcenavigation

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.slowSwipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
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
class UIExternalPositionTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkViews(): Unit = runBlocking {
        delay(1000)
        onView(withId(R.id.eta)).check(matches(isDisplayed()))
        onView(withId(R.id.rtt)).check(matches(isDisplayed()))
        onView(withId(R.id.rtd)).check(matches(isDisplayed()))
        onView(withId(R.id.top_panel)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_icon)).check(matches(isDisplayed()))
        onView(withId(R.id.instruction_distance)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_instruction)).check(matches(isDisplayed()))
    }

    @Test
    fun testFollowCursorButton(): Unit = runBlocking {
        delay(1000)
        onView(withId(R.id.gem_surface_view)).perform(slowSwipeLeft())
        delay(500)
        onView(withId(R.id.follow_gps_button)).check(matches(isDisplayed()))
        onView(withId(R.id.follow_gps_button)).perform(click())
    }
}
