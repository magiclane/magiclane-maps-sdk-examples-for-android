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


package com.magiclane.sdk.examples.routeinstructions

import android.view.View
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.assertion.ViewAssertions.selectedDescendantsMatch
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.IllegalArgumentException

// -------------------------------------------------------------------------------------------------
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UIRouteInstructionsInstrumentedTests
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
    }

    @After
    fun closeActivity()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun itemsShouldHaveTextDescriptionStatusAndImage()
    {
        onView(withId(R.id.list_view)).check(
            selectedDescendantsMatch(withId(R.id.text), not(withText("")))
        )
        onView(withId(R.id.list_view)).check(
            selectedDescendantsMatch(withId(R.id.status_text), not(withText("")))
        )
        onView(withId(R.id.list_view)).check(
            selectedDescendantsMatch(withId(R.id.status_description), not(withText("")))
        )
        onView(withId(R.id.list_view)).check(
            selectedDescendantsMatch(withId(R.id.turn_image), DrawableMatcher())
        )
    }

    private class DrawableMatcher : TypeSafeMatcher<View>()
    {

        override fun describeTo(description: Description?)
        {
            description?.appendText("Image does not have drawable")
        }

        override fun matchesSafely(item: View?): Boolean
        {
            if (item is ImageView)
                return item.drawable != null
            else throw IllegalArgumentException()
        }
    }

}
// -------------------------------------------------------------------------------------------------
