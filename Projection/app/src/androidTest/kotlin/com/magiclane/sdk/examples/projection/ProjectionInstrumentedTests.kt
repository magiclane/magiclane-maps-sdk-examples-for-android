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


package com.magiclane.sdk.examples.projection

import android.net.ConnectivityManager
import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class ProjectionInstrumentedTests
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: MainActivity
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerIdlingResource(): Unit = runBlocking {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        //IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
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
        //IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkProjectionResults(): Unit = runBlocking {
        delay(10000)
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
        val surface = activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view)
        val center = Pair(
            surface.measuredWidth / 2,
            surface.measuredHeight / 2
        )
        //540,880
        SdkCall.execute {
            surface.mapView?.centerOnCoordinates(
                Coordinates(45.651160,25.604815), //gm
                zoomLevel = -1,
                animation = Animation(EAnimation.None, duration = 0)
            )
        }

        delay(3000)

        onView(withId(R.id.gem_surface_view)).perform(
            touchDownAndUp(
                center.first.toFloat(),
                center.second.toFloat()
            )
        )
        delay(6000)

        //check there is at least a child with one of the substrings of projection types
        onView(withId(R.id.projections_list)).check(matches(hasMinimumChildCount(1)))
        onView(withId(R.id.projections_list)).perform(
            PerformTextCheck<MaterialTextView>(
                R.id.projection_name,
                anyOf(
                    withSubstring("WGS"),
                    withSubstring("WHATTHREEWORDS"),
                    withSubstring("LAM"),
                    withSubstring("BNG"),
                    withSubstring("MGRS"),
                    withSubstring("UTM"),
                    withSubstring("GK"),
                )
            )
        )
    }

    class PerformTextCheck<T : TextView>(@IdRes val textId: Int, private val matcher: Matcher<T>) : ViewAction
    {
        override fun getDescription(): String
        {
            return "Checking view with text matcher"
        }

        override fun getConstraints(): Matcher<View>
        {
            return isDisplayed()
        }

        override fun perform(uiController: UiController?, view: View?)
        {
            if (view is RecyclerView)
            {
                for (each in view.children)
                {
                    each.findViewById<T>(textId)?.let {
                        assert(matcher.matches(it)) { "View with id : $textId, did not match matcher : $matcher" }
                    }
                }
            } else
                Assert.fail("This action is for RecycleView only")
        }
    }

    private fun touchDownAndUp(x: Float, y: Float): ViewAction
    {
        return object : ViewAction
        {
            override fun getConstraints(): Matcher<View>
            {
                return isDisplayed()
            }

            override fun getDescription(): String
            {
                return "Send touch events."
            }

            override fun perform(uiController: UiController, view: View)
            {
                // Get view absolute position
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                // Offset coordinates by view position
                val coordinates = floatArrayOf(x + location[0], y + location[1])
                val precision = floatArrayOf(1f, 1f)

                // Send down event, pause, and send up
                val down = MotionEvents.sendDown(uiController, coordinates, precision).down
                uiController.loopMainThreadForAtLeast(200)
                MotionEvents.sendUp(uiController, down, coordinates)
            }
        }
    }
}
