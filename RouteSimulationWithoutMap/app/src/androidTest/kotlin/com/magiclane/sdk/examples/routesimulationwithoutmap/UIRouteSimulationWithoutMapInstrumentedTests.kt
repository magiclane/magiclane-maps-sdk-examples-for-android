// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.routesimulationwithoutmap

// -------------------------------------------------------------------------------------------------
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UIRouteSimulationWithoutMapInstrumentedTests
{
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

    @Test
    fun checkMandatoryViewsForVisibilityAndText()
    {
        //speed indicator
        onView(withId(R.id.nav_current_speed)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.nav_current_speed_limit)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.nav_current_speed_unit)).check(
            matches(
                allOf(
                    withText("km/h"),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.turn_distance)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.turn_distance_unit)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.turn_instruction)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.eta)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.rtt)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.rtd)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )
        onView(withId(R.id.current_street_text)).check(
            matches(
                allOf(
                    not(withText("")),
                    isDisplayed()
                )
            )
        )

    }
}
// -------------------------------------------------------------------------------------------------
