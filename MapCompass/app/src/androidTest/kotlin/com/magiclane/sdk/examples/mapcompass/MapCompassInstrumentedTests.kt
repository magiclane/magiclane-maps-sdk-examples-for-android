/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapcompass

import android.hardware.SensorManager
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MapCompassInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var mActivityIdlingResource: IdlingResource? = null

    private lateinit var activityRes: MainActivity
    private lateinit var sensorManager: SensorManager

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        activityScenarioRule.scenario.onActivity { activity ->
            mActivityIdlingResource = activity.getActivityIdlingResource()
            IdlingRegistry.getInstance().register(mActivityIdlingResource)
            activityRes = activity
            sensorManager = activity.getSystemService(SensorManager::class.java)
        }
    }

    @After
    fun tearDown() {
        if (mActivityIdlingResource != null) {
            IdlingRegistry.getInstance().unregister(mActivityIdlingResource)
        }
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkLiveRotationText() {
        onView(withId(R.id.surface_view)).check(matches(isDisplayed()))
        val disabledText = activityRes.resources.getString(R.string.live_heading_disabled)
        val enabledText = activityRes.resources.getString(R.string.live_heading_enabled)
        onView(withId(R.id.status_text)).check(matches(withText(disabledText)))
        onView(withId(R.id.btn_enable_live_heading)).perform(click())
        onView(withId(R.id.status_text)).check(matches(withText(enabledText)))
    }

    @Test
    fun checkViews() {
        onView(withId(R.id.surface_view)).check(matches(isDisplayed()))
        val greenColor = ResourcesCompat.getColorStateList(
            activityRes.resources,
            R.color.primary,
            activityRes.theme,
        )
        val redColor = ResourcesCompat.getColorStateList(
            activityRes.resources,
            R.color.surface,
            activityRes.theme,
        )
        onView(withId(R.id.btn_enable_live_heading)).check { view, ex ->
            if (ex != null) throw ex
            if (view is FloatingActionButton) {
                assertEquals(
                    "red canal for color green",
                    view.backgroundTintList!!.defaultColor.red,
                    greenColor?.defaultColor?.red,
                )
                assertEquals(
                    "blue canal for color green",
                    view.backgroundTintList!!.defaultColor.blue,
                    greenColor?.defaultColor?.blue,
                )
                assertEquals(
                    "green canal for color green",
                    view.backgroundTintList!!.defaultColor.green,
                    greenColor?.defaultColor?.green,
                )
            }
        }
        onView(withId(R.id.btn_enable_live_heading)).perform(click())

        onView(withId(R.id.btn_enable_live_heading)).check { view, ex ->
            if (ex != null) throw ex
            if (view is FloatingActionButton) {
                assertEquals(
                    "red canal for color red",
                    view.backgroundTintList!!.defaultColor.red,
                    redColor?.defaultColor?.red,
                )
                assertEquals(
                    "blue canal for color red",
                    view.backgroundTintList!!.defaultColor.blue,
                    redColor?.defaultColor?.blue,
                )
                assertEquals(
                    "green canal for color red",
                    view.backgroundTintList!!.defaultColor.green,
                    redColor?.defaultColor?.green,
                )
            }
        }
        onView(withId(R.id.compass)).check(matches(isDisplayed()))
    }
}
