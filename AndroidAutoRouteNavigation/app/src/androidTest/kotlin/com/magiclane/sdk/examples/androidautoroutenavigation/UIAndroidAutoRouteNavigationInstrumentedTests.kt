/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation

import android.Manifest
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.google.android.material.appbar.MaterialToolbar
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.examples.androidautoroutenavigation.activities.MainActivity
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI (Espresso) tests for the phone-side [MainActivity].
 *
 * Verifies that the map surface and toolbar are shown and that no route calculation is left
 * running (the progress indicator stays hidden) after startup. The Android Auto screens need a
 * car host and are not exercised here; SDK-level behaviour is covered by
 * [AndroidAutoRouteNavigationInstrumentedTest].
 */
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UIAndroidAutoRouteNavigationInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()

        private const val SURFACE_TIMEOUT_MS = 10_000L
    }

    // Location is granted up front: the map follows the current position on startup and a pending
    // permission dialog would block Espresso (a denial would even exit the process, see
    // MainActivityController.onRequestPermissionsResult).
    @Rule(order = 0)
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @Rule(order = 1)
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        activityScenarioRule.scenario.close()
    }

    /**
     * All UI assertions are performed in a single test method on purpose.
     *
     * [MainActivity.onDestroy] normally calls `exitProcess(0)` (guarded under instrumentation) and
     * pressing back does so unconditionally — the activity is designed for exactly one lifecycle
     * per process. Like the other example UI tests, everything is verified within one activity
     * lifecycle to keep the test independent of that teardown behaviour.
     */
    @Test
    fun mapLoadsToolbarShownAndNoRouteCalculationPending() {
        // 1. The SDK creates the surface's map view asynchronously after the activity resumes, so
        //    wait for its onDefaultMapViewCreated callback before asserting. The toolbar overlaps
        //    the top of the surface, so it is not *completely* displayed; asserting it is visible
        //    (partially on screen) is correct here.
        waitForMapViewCreated(SURFACE_TIMEOUT_MS)
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))

        // 2. The toolbar is shown and displays the app name.
        val expectedTitle = activityScenarioRule.scenario.let {
            var title = ""
            it.onActivity { activity -> title = activity.getString(R.string.app_name) }
            title
        }
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        onView(withId(R.id.toolbar)).check(matchesToolbarTitle(expectedTitle))

        // 3. Routing here is only started from the Android Auto screens, so after startup the
        //    progress indicator must be hidden and the map surface still shown (no blocking
        //    error dialog took over).
        onView(withId(R.id.progress_bar))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.gem_surface)).check(matches(isDisplayed()))
    }

    // Waits until the SDK reports (via onDefaultMapViewCreated) that the map view backing the
    // surface has been created, or the timeout elapses. The activity registered its own callback
    // for focus-viewport alignment, so it is chained rather than replaced. Timeouts are not fatal
    // here: the Espresso assertions that follow report the actual failure.
    private fun waitForMapViewCreated(timeoutMs: Long) {
        val latch = CountDownLatch(1)
        activityScenarioRule.scenario.onActivity { activity ->
            val surfaceView = activity.findViewById<GemSurfaceView>(R.id.gem_surface)
            if (surfaceView.mapView != null) {
                latch.countDown()
            } else {
                val activityCallback = surfaceView.onDefaultMapViewCreated
                surfaceView.onDefaultMapViewCreated = { mapView ->
                    activityCallback?.invoke(mapView)
                    latch.countDown()
                }
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun matchesToolbarTitle(expected: String) = ViewAssertion { view: View?, noView: NoMatchingViewException? ->
        if (view == null) throw noView ?: AssertionError("Toolbar not found")
        val actual = (view as MaterialToolbar).title?.toString()
        assert(actual == expected) { "Toolbar title was \"$actual\", expected \"$expected\"." }
    }
}
