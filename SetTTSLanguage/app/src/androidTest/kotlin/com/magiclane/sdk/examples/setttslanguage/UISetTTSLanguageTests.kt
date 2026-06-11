/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.setttslanguage

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UISetTTSLanguageTests {
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
        // Skip UI tests if TTS languages are not available
        runBlocking {
            var hasTts = false
            repeat(10) {
                val languages = SdkCall.execute { SoundPlayingService.getTTSLanguages() }
                if (languages?.isNotEmpty() == true) {
                    hasTts = true
                    return@repeat
                }
                delay(2000)
            }
            Assume.assumeTrue(
                "TTS languages not available on this device",
                hasTts,
            )
        }

        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        EspressoIdlingResource.increment()
        activityScenarioRule.scenario.onActivity { _ ->
            EspressoIdlingResource.decrement()
        }
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun testVisibility() {
        onView(withId(R.id.language_button)).check(matches(isDisplayed()))
        onView(withId(R.id.language_value)).check(matches(isDisplayed()))
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
        onView(withId(R.id.language_button)).perform(click())
        onView(withId(R.id.list_view)).check(matches(isDisplayed()))

        // check to see if there are items displayed
        onView(withId(R.id.list_view)).check(RecyclerViewItemCountAssertion(greaterThan(0)))
    }

    @Test
    fun languageShouldChangeOnItemClick() {
        onView(withId(R.id.language_button)).perform(click())
        onView(withId(R.id.list_view)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText("deu-DEU")),
                click(),
            ),
        )
        onView(withId(R.id.language_value)).check(matches(withText("Deutsch")))
    }

    class RecyclerViewItemCountAssertion(private val matcher: Matcher<Int>) : ViewAssertion {

        override fun check(view: View?, noViewFoundException: NoMatchingViewException?) {
            view?.let {
                val recyclerView = view as RecyclerView
                val nResults = recyclerView.adapter?.itemCount
                if (nResults != null) {
                    ViewMatchers.assertThat(nResults, matcher)
                } else {
                    throw Error("No adapter attached")
                }
            } ?: noViewFoundException
        }
    }
}
