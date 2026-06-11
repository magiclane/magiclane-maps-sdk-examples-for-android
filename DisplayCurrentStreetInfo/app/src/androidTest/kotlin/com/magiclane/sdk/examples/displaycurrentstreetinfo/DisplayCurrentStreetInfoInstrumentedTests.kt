/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.displaycurrentstreetinfo

import android.Manifest
import android.location.LocationManager
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
import androidx.test.rule.GrantPermissionRule
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
class DisplayCurrentStreetInfoInstrumentedTests {

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

    private var positionIsValid = true

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
            IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        }
        positionIsValid = activityRes.getSystemService(
            LocationManager::class.java,
        ).isLocationEnabled
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkStatusViewIsVisible(): Unit = runBlocking {
        if (positionIsValid) {
            delay(2000)
            onView(withId(R.id.current_street_name)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testFollowCursorButton(): Unit = runBlocking {
        if (positionIsValid) {
            delay(2000)
            onView(withId(R.id.gem_surface)).perform(slowSwipeLeft())
            delay(500)
            onView(withId(R.id.follow_cursor_button)).check(matches(isDisplayed()))
            onView(withId(R.id.follow_cursor_button)).perform(click())
        }
    }
}
