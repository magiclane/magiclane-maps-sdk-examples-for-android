// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.multiplesurfacesinfragment

import android.net.ConnectivityManager
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SurfacesInFragmentRecyclerTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: MainActivity
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
    }

    @Test
    fun addMapTest()
    {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.bottom_right_button)).perform(click())
        assert(activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount == 2){
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount
        }
    }


    @Test
    fun removeMapTest()
    {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.bottom_left_button)).perform(click())
        assert(activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount == 0){
            activityRes.findViewById<LinearLayout>(R.id.scrolled_linear_layout).childCount
        }
    }

}
