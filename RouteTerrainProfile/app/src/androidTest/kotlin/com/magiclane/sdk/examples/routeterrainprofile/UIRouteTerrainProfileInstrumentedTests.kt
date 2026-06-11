/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routeterrainprofile

import androidx.lifecycle.Lifecycle
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
class UIRouteTerrainProfileInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var routingProfile: RouteProfile? = null
    private var elevationIconSize = 0
    private lateinit var activityRef: MainActivity

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRef = activity
            elevationIconSize =
                activity.resources.getDimension(R.dimen.elevation_button_size).toInt()
        }
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        if (routingProfile != null) routingProfile = null
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkContainerButtonsWork() {
        // in order to use the idling resource we need to call an espresso action first
        onView(withText(R.string.app_name)).check(matches(isDisplayed()))
        // get the routing profile view once the app is idle
        routingProfile = activityRef.routingProfile
        assert(routingProfile != null)
        for (buttonIndex in 0 until 4) {
            onView(withId(buttonIndex + 100)).perform(click())
            runBlocking { delay(5000) }
        }
    }
}
