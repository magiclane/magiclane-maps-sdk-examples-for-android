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
import android.net.ConnectivityManager
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class MapSelectionInstrumentedTests {
    
    private lateinit var activityRes: MainActivity
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    @Rule
    @JvmField
    val mapSelectionRule: RuleChain = RuleChain.outerRule(activityScenarioRule).around(permissionRule)

    @Before
    fun registerIdlingResource() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity() {
        activityScenarioRule.scenario.close()
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
    }

    @Test
    fun selectMyPosition(): Unit = runBlocking {
        delay(10000)
        onView(withId(R.id.follow_cursor)).perform(click())

        val center = SdkCall.execute {
            activityRes.gemSurfaceView.mapView?.viewport?.center?.run {
                Pair(x, y)
            }
        }!!

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
        onView(withId(R.id.gem_surface)).perform(CenterAndTouch(40.689846, -74.047690))
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
    private fun touchDownAndUp(x: Float, y: Float): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isDisplayed()
            }

            override fun getDescription(): String {
                return "Send touch events."
            }

            override fun perform(uiController: UiController, view: View) {
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

    class CenterAndTouch(private val lat: Double, private val lon: Double) : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return withClassName(`is`(GemSurfaceView::class.qualifiedName))
        }

        override fun getDescription(): String {
            return "GemSurfaceView class perform Center and Touch event"
        }

        override fun perform(uiController: UiController?, view: View?) {
            view?.let {
                (view as GemSurfaceView).apply {
                    CoroutineScope(Dispatchers.Main).launch {
                        SdkCall.execute {
                            val coordinates = Coordinates(lat, lon)
                            mapView?.centerOnCoordinates(coordinates, animation = Animation(EAnimation.None, duration = 0))
                        }
                        delay(2000)
                        SdkCall.execute {
                            val center = mapView?.viewport?.center
                            if (center != null)
                                Util.postOnMain { mapView?.onTouch?.invoke(center) }
                        }
                    }
                }
            } ?: throw IllegalArgumentException()
        }

    }

}
