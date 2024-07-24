// -------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.multisurfinfragrecycler

import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.multisurfinfragrecycler.R
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
        assert((activityRes.findViewById<RecyclerView>(R.id.list).adapter as CustomAdapter).itemCount == 6)
    }


    @Test
    fun removeMapTest()
    {
        onView(withId(R.id.button_first)).perform(click())
        onView(withId(R.id.bottom_left_button)).perform(click())
        assert((activityRes.findViewById<RecyclerView>(R.id.list).adapter as CustomAdapter).itemCount == 4)
    }

}
