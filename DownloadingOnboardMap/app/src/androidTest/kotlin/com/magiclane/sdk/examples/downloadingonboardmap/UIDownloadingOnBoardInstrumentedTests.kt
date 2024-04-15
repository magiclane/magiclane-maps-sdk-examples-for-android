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

package com.magiclane.sdk.examples.downloadingonboardmap

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withParentIndex
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UIDownloadingOnBoardInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var navIdleResource: IdlingResource? = null
    private var downloadIdleResource: IdlingResource? = null

    private lateinit var activityRes: MainActivity


    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            IdlingPolicies.setIdlingResourceTimeout(180000, TimeUnit.MILLISECONDS)
            activityRes = activity
        }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        if (navIdleResource != null)
            IdlingRegistry.getInstance().unregister(navIdleResource)
        if (downloadIdleResource != null)
            IdlingRegistry.getInstance().unregister(downloadIdleResource)
    }

    @Test
    fun checkMapDownloading(): Unit = runBlocking {
        delay(5000)
        onView(
            withPositionInParent(
                R.id.list_view,
                0
            )
        ).check(
            matches(
                allOf(
                    withChild(withText("Afghanistan")), withChild(
                        allOf(
                            withId(R.id.item_progress_bar),
                            isDisplayed()
                        )
                    )
                )
            )
        )
    }

    fun withPositionInParent(parentViewId: Int, position: Int): Matcher<View> =
        allOf(withParent(withId(parentViewId)), withParentIndex(position))

}