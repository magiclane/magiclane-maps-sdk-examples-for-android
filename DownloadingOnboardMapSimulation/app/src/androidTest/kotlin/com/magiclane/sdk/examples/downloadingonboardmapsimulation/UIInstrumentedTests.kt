// -------------------------------------------------------------------------------------------------------------------------------

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

package com.magiclane.sdk.examples.downloadingonboardmapsimulation

import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
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
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UIInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var activityRes: MainActivity
    private val contentStore = ContentStore()

    @Before
    fun registerIdlingResource(): Unit = runBlocking {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        delay(2000)
        activityScenarioRule.scenario.onActivity { activity ->
            IdlingPolicies.setIdlingResourceTimeout(120000, TimeUnit.MILLISECONDS)
            IdlingRegistry.getInstance().register(EspressoIdlingResource.navigationIdlingResource)
            activityRes = activity
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        SdkCall.execute {
            val list = contentStore.getLocalContentList(EContentType.RoadMap)
            list?.forEach {
                if (it.canDeleteContent()) it.deleteContent()
            }
        }
        activityScenarioRule.scenario.onActivity { activity ->
            activity.finish()
        }

        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.navigationIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun t2_checkViews(): Unit = runBlocking {
        onView(withId(R.id.eta)).check(matches(isDisplayed()))
        onView(withId(R.id.rtt)).check(matches(isDisplayed()))
        onView(withId(R.id.rtd)).check(matches(isDisplayed()))
        onView(withId(R.id.top_panel)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_instruction_icon)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_instruction_distance)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_instruction)).check(matches(isDisplayed()))
    }

    @Test
    fun t3_testFollowCursorButton(): Unit = runBlocking {
        onView(withId(R.id.gem_surface_view)).perform(slowSwipeLeft())
        delay(500)
        onView(withId(R.id.follow_cursor_button)).check(matches(isDisplayed()))
        onView(withId(R.id.follow_cursor_button)).perform(click())
    }
}
