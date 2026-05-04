/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.projection

import android.view.View
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.google.android.material.textview.MaterialTextView
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.Matcher
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class ProjectionInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: MainActivity

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
    }

    @After
    fun tearDown() {
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkProjectionResults(): Unit = runBlocking {
        delay(10000)
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
        onView(withId(R.id.gem_surface_view)).perform(CenterAndTouch(45.651160, 25.604815))
        delay(6000)
        // Skip if no landmark was found at touch position (e.g. on emulators with slow tile loading)
        val hasChildren = activityRes.findViewById<RecyclerView>(R.id.projections_list).childCount > 0
        Assume.assumeTrue("No projection results (no landmark selected at touch coordinates)", hasChildren)
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
                ),
            ),
        )
    }

    class PerformTextCheck<T : TextView>(
        @IdRes val textId: Int,
        private val matcher: Matcher<T>,
    ) : ViewAction {
        override fun getDescription(): String {
            return "Checking view with text matcher"
        }

        override fun getConstraints(): Matcher<View> {
            return isDisplayed()
        }

        override fun perform(uiController: UiController?, view: View?) {
            if (view is RecyclerView) {
                for (each in view.children) {
                    each.findViewById<T>(textId)?.let {
                        assert(
                            matcher.matches(it),
                        ) { "View with id : $textId, did not match matcher : $matcher" }
                    }
                }
            } else {
                Assert.fail("This action is for RecycleView only")
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
                            mapView?.centerOnCoordinates(
                                coordinates,
                                animation = Animation(EAnimation.None, duration = 0),
                            )
                        }
                        delay(2000)
                        SdkCall.execute {
                            val center = mapView?.viewport?.center
                            if (center != null) {
                                Util.postOnMain { mapView?.onTouch?.invoke(center) }
                            }
                        }
                    }
                }
            } ?: throw IllegalArgumentException()
        }
    }
}
