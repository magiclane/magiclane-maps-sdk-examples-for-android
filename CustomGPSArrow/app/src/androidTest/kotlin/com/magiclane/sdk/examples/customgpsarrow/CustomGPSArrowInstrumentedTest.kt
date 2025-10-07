/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.customgpsarrow

import android.net.ConnectivityManager
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
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class CustomGPSArrowInstrumentedTest
{

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)
    
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    
    @Before
    fun registerIdlingResource(): Unit = runBlocking {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        delay(2000)
        activityScenarioRule.scenario.onActivity { activity ->
            IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }
    @Test
    fun testFollowCursorButton(): Unit = runBlocking {
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
        onView(withId(R.id.gem_surface)).perform(slowSwipeLeft())
        delay(3000)
        onView(withId(R.id.followCursor)).check(matches(isDisplayed()))
        onView(withId(R.id.followCursor)).perform(click())
    }
}
