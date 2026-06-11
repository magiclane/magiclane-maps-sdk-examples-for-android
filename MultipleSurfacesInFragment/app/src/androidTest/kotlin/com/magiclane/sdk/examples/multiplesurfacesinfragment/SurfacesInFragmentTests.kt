/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.multiplesurfacesinfragment

import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SurfacesInFragmentRecyclerTests {

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
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
    }

    @After
    fun tearDown() {
        activityScenarioRule.scenario.close()
    }

    @Test
    fun addMapTest() {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.remove_surface_button)).perform(click())
        assert(
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount == 2,
        ) {
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount
        }
    }

    @Test
    fun removeMapTest() {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.bottom_left_button)).perform(click())
        assert(
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount == 0,
        ) {
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount
        }
    }
}
