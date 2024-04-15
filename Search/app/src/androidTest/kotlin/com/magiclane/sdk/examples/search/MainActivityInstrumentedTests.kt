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


package com.magiclane.sdk.examples.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParentIndex
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.core.AllOf.allOf
import org.hamcrest.core.Is.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MainActivityInstrumentedTests
{
    companion object{
        const val WAIT_TIME = 15000L
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mainIntent = Intent(context, MainActivity::class.java)

    @get:Rule
    val activityRule = ActivityScenarioRule<MainActivity>(mainIntent)

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @Before
    fun beforeTest()
    {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
    }

    @After
    fun afterTest()
    {
        //activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        activityRule.scenario.close()
    }

    @Test
    fun firstItemMustContainSubstringOfSearch() {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(typeText("London"))
        runBlocking { delay(WAIT_TIME) }
        onView(allOf(withParentIndex(0),isAssignableFrom(TextView::class.java), withText("London"))).check(matches(isDisplayed()))
    }

    @Test
    fun onNoTextChangedResultsShouldNotBeDisplayed()
    {
        closeSoftKeyboard()
        onView(
            allOf(
                withId(R.id.list_view),
                isDisplayed()
            )
        ).check(RecyclerViewItemCountAssertion(`is`(0)))
    }

    @Test
    fun onTextChangedResultsShouldBeDisplayed()
    {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(typeText("a"))
        runBlocking { delay(WAIT_TIME) }
        closeSoftKeyboard()
        onView(allOf(withId(R.id.list_view), isDisplayed()))
            .check(RecyclerViewItemCountAssertion(greaterThan(0)))
    }

    @Test
    fun abcTest()
    {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(
            typeText("abc")
        )
        closeSoftKeyboard()
        onView(allOf(withId(R.id.search_input), isDisplayed())).check(
            SearchViewTextAssertionAssistant(`is`("abc"))
        )
    }

    @Test
    fun noResultTest()
    {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(
            typeText("yyqqp")
        )
        runBlocking { delay(WAIT_TIME) }
        closeSoftKeyboard()
        onView(
            allOf(
                withId(R.id.list_view),
                isDisplayed()
            )
        ).check(RecyclerViewItemCountAssertion(`is`(0)))
        onView(withId(R.id.no_results_text)).check(matches(isDisplayed()))
    }

    class RecyclerViewItemCountAssertion : ViewAssertion
    {
        private val matcher: Matcher<Int>

        constructor(matcher: Matcher<Int>)
        {
            this.matcher = matcher
        }

        override fun check(view: View?, noViewFoundException: NoMatchingViewException?)
        {
            view?.let {
                val recyclerView = view as RecyclerView
                val nResults = recyclerView.adapter?.itemCount
                if (nResults != null)
                    assertThat(nResults, matcher)
                else
                    throw Error("No adapter attached")
            } ?: noViewFoundException
        }
    }

    class SearchViewTextAssertionAssistant(private val matcher: Matcher<String>) : ViewAssertion
    {

        override fun check(view: View?, noViewFoundException: NoMatchingViewException?)
        {
            view?.let {
                val searchView = view as SearchView
                val str = searchView.query.toString()
                assertThat(str, matcher)
            } ?: noViewFoundException
        }
    }
}