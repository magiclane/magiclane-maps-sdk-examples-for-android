// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.truckprofile

// -------------------------------------------------------------------------------------------------

import android.net.ConnectivityManager
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


// -------------------------------------------------------------------------------------------------

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UITruckProfileInstrumentedTests
{
    // -------------------------------------------------------------------------------------------------
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
    }

    // -------------------------------------------------------------------------------------------------

    @Test
    fun onSaveClickDialogIsShown()
    {
        onView(withId(R.id.settings_button)).perform(click())
        onView(withText(R.string.app_name)).check(matches(isDisplayed()))
        activityScenarioRule.scenario.close()
    }

    @Test
    fun recyclerViewHasTruckProfileSettingsShown()
    {
        val supposedSettingsCount = MainActivity.ETruckProfileSettings.entries.size

        onView(withId(R.id.settings_button)).perform(click())

        onView(withText(R.string.weight)).check(matches(isDisplayed()))
        onView(withText(R.string.height)).check(matches(isDisplayed()))
        onView(withText(R.string.length)).check(matches(isDisplayed()))
        onView(withText(R.string.width)).check(matches(isDisplayed()))
        onView(withId(R.id.truck_profile_settings_list)).perform(
            RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(
                supposedSettingsCount - 1
            )
        )
        onView(withText(R.string.axle_weight)).check(matches(isDisplayed()))
        onView(withText(R.string.max_speed)).check(matches(isDisplayed()))
    }

    @Test
    fun checkProfileSettingUpdated()
    {
        val settingsCount = MainActivity.ETruckProfileSettings.entries.size

        onView(withId(R.id.settings_button)).perform(click())

        onView(withContentDescription(R.string.weight)).perform(setValue(10f))
        onView(withContentDescription(R.string.height)).perform(setValue(3f))
        onView(withContentDescription(R.string.length)).perform(setValue(10f))
        onView(withContentDescription(R.string.width)).perform(setValue(3f))

        onView(withId(R.id.truck_profile_settings_list)).perform(
            RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(
                settingsCount - 1
            )
        )

        onView(withContentDescription(R.string.axle_weight)).perform(setValue(10f))
        onView(withContentDescription(R.string.max_speed)).perform(setValue(100f))

        onView(withText(R.string.save)).perform(click())
        onView(withId(R.id.settings_button)).perform(click())

        onView(withContentDescription(R.string.weight)).check(
            matches(withValue(10f))
        )
        onView(withContentDescription(R.string.height)).check(
            matches(withValue(3f))
        )
        onView(withContentDescription(R.string.length)).check(
            matches(withValue(10f))
        )
        onView(withContentDescription(R.string.width)).check(
            matches(withValue(3f))
        )

        onView(withId(R.id.truck_profile_settings_list)).perform(
            RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(
                settingsCount - 1
            )
        )

        onView(withContentDescription(R.string.axle_weight)).check(
            matches(withValue(10f))
        )
        onView(withContentDescription(R.string.max_speed)).check(
            matches(withValue(100f))
        )

    }
    // -------------------------------------------------------------------------------------------------

    /** not tests*/

    /**
     * Matcher to check a slider's value
     **/
    private fun withValue(expectedValue: Float): Matcher<View?>
    {
        return object : BoundedMatcher<View?, Slider>(Slider::class.java)
        {
            override fun describeTo(description: Description)
            {
                description.appendText("expected: $expectedValue")
            }

            override fun matchesSafely(slider: Slider?): Boolean
            {
                return slider?.value == expectedValue
            }
        }
    }

    /**
     * ViewAction for setting a Slider's value
     */
    fun setValue(value: Float): ViewAction
    {
        return object : ViewAction
        {
            override fun getDescription(): String
            {
                return "Set Slider value to $value"
            }

            override fun getConstraints(): Matcher<View>
            {
                return ViewMatchers.isAssignableFrom(Slider::class.java)
            }

            override fun perform(uiController: UiController?, view: View)
            {
                val seekBar = view as Slider
                seekBar.value = value
            }
        }
    }
}
// -------------------------------------------------------------------------------------------------
