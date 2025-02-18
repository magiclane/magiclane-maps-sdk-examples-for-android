// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
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
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UISearchTests
{
    companion object
    {
        const val WAIT_TIME = 5000L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext: Context = instrumentation.targetContext
    private val mainIntent = Intent(appContext, MainActivity::class.java)
    
    private val activityRule = ActivityScenarioRule<MainActivity>(mainIntent)
    
    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    
    @Rule
    @JvmField
    val chainRule = RuleChain.outerRule(permissionRule).around(activityRule)
    

    @Before
    fun beforeTest(): Unit = runBlocking {
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingPolicies.setIdlingResourceTimeout(1, TimeUnit.MINUTES)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        EspressoIdlingResource.increment()
        activityRule.scenario.onActivity {
            EspressoIdlingResource.decrement()
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun afterTest()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityRule.scenario.close()
    }

    @Test
    fun firstItemMustContainSubstringOfSearch()
    {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(typeText("London"))
        runBlocking { delay(WAIT_TIME) }
        onView(allOf(withParentIndex(0), isAssignableFrom(TextView::class.java), withText("London"))).check(matches(isDisplayed()))
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
    fun onTextChangedResultsShouldBeDisplayed(): Unit = runBlocking {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(typeText("a"))
        delay(WAIT_TIME)
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
    fun noResultTest(): Unit = runBlocking {
        onView(allOf(withId(R.id.search_input), isDisplayed())).perform(
            typeText("yyqqp")
        )
        delay(WAIT_TIME)
        closeSoftKeyboard()
        onView(
            allOf(
                withId(R.id.list_view),
                isDisplayed()
            )
        ).check(RecyclerViewItemCountAssertion(`is`(0)))
        onView(withId(R.id.no_results_text)).check(matches(isDisplayed()))
    }

    class RecyclerViewItemCountAssertion(private val matcher: Matcher<Int>) : ViewAssertion
    {

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
