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


package com.magiclane.sdk.examples.mapselection

import android.Manifest
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MapSelectionInstrumentedTests
{

    @Rule(order = 1)
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private var mActivityIdlingResource: IdlingResource? = null

    private lateinit var activityRes: MainActivity


    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            mActivityIdlingResource = activity.getActivityIdlingResource()
            // To prove that the test fails, omit this call:
            IdlingRegistry.getInstance().register(mActivityIdlingResource)
            activityRes = activity
        }
    }

    @Rule(order = 0)
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        if (mActivityIdlingResource != null)
            IdlingRegistry.getInstance().unregister(mActivityIdlingResource)
    }

    @Test
    fun selectMyPosition(): Unit = runBlocking {
        delay(5000)
        onView(withId(R.id.follow_cursor)).perform(click())

        val offset = activityRes.resources.getDimension(R.dimen.offset)

        val center = Pair(
            activityRes.gemSurfaceView.measuredWidth / 2,
            activityRes.gemSurfaceView.measuredHeight / 3 * 2 + offset
        )

        onView(withId(R.id.gem_surface)).perform(
            touchDownAndUp(
                center.first.toFloat(),
                center.second.toFloat()
            )
        )

        onView(withId(R.id.name)).check(matches(isDisplayed()))
    }


    @Test
    fun selectLandmark(): Unit = runBlocking {
        delay(5000)
        val center = Pair(
            activityRes.gemSurfaceView.measuredWidth / 2,
            activityRes.gemSurfaceView.measuredHeight / 2
        )
        async {
            SdkCall.execute {

                val centerXy = Xy(center.first, center.second)
                activityRes.gemSurfaceView.mapView?.centerOnCoordinates(
                    Coordinates(40.689846, -74.047690), //Liberty Island
                    zoomLevel = -1,
                    xy = centerXy,
                )
            }
        }.await()
        delay(5000)

        onView(withId(R.id.gem_surface)).perform(
            touchDownAndUp(
                center.first.toFloat(),
                center.second.toFloat()
            )
        )
        delay(5000)
        onView(ViewMatchers.withSubstring("Liberty Island")).check(matches(isDisplayed()))
    }

    @Test
    fun centerOnRoutes(): Unit = runBlocking {
        delay(5000)
        onView(withId(R.id.fly_to_route)).perform(click())
        assert(activityRes.routesList.size >= 2) { activityRes.routesList.size }
    }

    /**
     * not a test
     */
    private fun touchDownAndUp(x: Float, y: Float): ViewAction?
    {
        return object : ViewAction
        {
            override fun getConstraints(): Matcher<View>
            {
                return ViewMatchers.isDisplayed()
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