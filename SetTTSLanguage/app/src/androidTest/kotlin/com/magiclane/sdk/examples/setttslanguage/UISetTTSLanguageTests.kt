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


package com.magiclane.sdk.examples.setttslanguage

import android.net.ConnectivityManager
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
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import org.hamcrest.Matcher
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UISetTTSLanguageTests
{
    // -------------------------------------------------------------------------------------------------

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        EspressoIdlingResource.increment()
        activityScenarioRule.scenario.onActivity { _ ->
            EspressoIdlingResource.decrement()
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

    // -------------------------------------------------------------------------------------------------
    @Test
    fun testVisibility()
    {
        onView(withId(R.id.choose_language_button)).check(matches(isDisplayed()))
        onView(withId(R.id.language_value)).check(matches(isDisplayed()))
        onView(withId(R.id.play_button)).check(matches(isDisplayed()))
        onView(withId(R.id.choose_language_button)).perform(click())
        onView(withId(R.id.list_view)).check(matches(isDisplayed()))

        //check to see if there are items displayed
        onView(withId(R.id.list_view)).check(RecyclerViewItemCountAssertion(greaterThan(0)))

    }

    @Test
    fun languageShouldChangeOnItemClick()
    {
        onView(withId(R.id.choose_language_button)).perform(click())
        onView(withId(R.id.list_view)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText("deu-DEU")), click())
        )
        onView(withId(R.id.language_value)).check(matches(withText("Deutsch")))
    }


    class RecyclerViewItemCountAssertion(private val matcher: Matcher<Int>) : ViewAssertion
    {

        override fun check(view: View?, noViewFoundException: NoMatchingViewException?)
        {
            view?.let {
                val recyclerView = view as RecyclerView
                val nResults = recyclerView.adapter?.itemCount
                if (nResults != null)
                    ViewMatchers.assertThat(nResults, matcher)
                else
                    throw Error("No adapter attached")
            } ?: noViewFoundException
        }
    }
}
