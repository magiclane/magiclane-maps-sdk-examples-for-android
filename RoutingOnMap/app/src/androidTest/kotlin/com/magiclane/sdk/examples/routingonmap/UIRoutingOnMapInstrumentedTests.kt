/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.routingonmap

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
 * UI (Espresso) tests for [MainActivity].
 *
 * Verifies that the map surface and toolbar are shown and that the London -> Paris route the
 * example calculates on startup completes (the progress indicator disappears) without surfacing
 * an error. SDK-level routing behaviour is covered by [RoutingOnMapInstrumentedTest].
 */
@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UIRoutingOnMapInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()

        private const val ROUTE_TIMEOUT_MS = 30_000L
        private const val SURFACE_TIMEOUT_MS = 10_000L
        private const val POLL_INTERVAL_MS = 500L
    }

    @Rule(order = 0)
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
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
     * [MainActivity.onDestroy] calls `GemSdk.release()` and `exitProcess(0)`, which terminates the
     * whole process — including the instrumentation. A second `@Test` would relaunch the activity
     * in a process that has already been killed by the first test's teardown, so this class (like
     * the other example UI tests) verifies everything within one activity lifecycle.
     */
    @Test
    fun mapLoadsToolbarShownAndRouteCompletesWithoutError() {
        // 1. The SDK creates the surface's map view asynchronously after the activity resumes, so
        //    wait for its onDefaultMapViewCreated callback before asserting. The toolbar overlaps
        //    the top of the surface, so it is not *completely* displayed; asserting it is visible
        //    (partially on screen) is correct here.
        waitForMapViewCreated(SURFACE_TIMEOUT_MS)
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))

        // 2. The toolbar is shown and displays the app name.
        val expectedTitle = activityScenarioRule.scenario.let {
            var title = ""
            it.onActivity { activity -> title = activity.getString(R.string.app_name) }
            title
        }
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        onView(withId(R.id.toolbar)).check(matchesToolbarTitle(expectedTitle))

        // 3. The example starts calculating the route once map data is ready; the progress
        //    indicator is shown while it runs and hidden on completion. Wait for it to settle.
        waitForProgressHidden(ROUTE_TIMEOUT_MS)
        onView(withId(R.id.progress_bar))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        // The map surface must still be shown (no blocking error dialog took over).
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
    }

    // Waits until the SDK reports (via onDefaultMapViewCreated) that the map view backing the
    // surface has been created, or the timeout elapses. The activity registered its own callback
    // for focus-viewport alignment, so it is chained rather than replaced. Timeouts are not fatal
    // here: the Espresso assertions that follow report the actual failure.
    private fun waitForMapViewCreated(timeoutMs: Long) {
        val latch = CountDownLatch(1)
        activityScenarioRule.scenario.onActivity { activity ->
            val surfaceView = activity.findViewById<GemSurfaceView>(R.id.gem_surface_view)
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

    // Polls the progress indicator until it is no longer visible, or the timeout elapses.
    private fun waitForProgressHidden(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isProgressHidden()) return
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun isProgressHidden(): Boolean = try {
        onView(withId(R.id.progress_bar))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
        true
    } catch (_: AssertionError) {
        false
    } catch (_: NoMatchingViewException) {
        false
    }

    private fun matchesToolbarTitle(expected: String) = ViewAssertion { view: View?, noView: NoMatchingViewException? ->
        if (view == null) throw noView ?: AssertionError("Toolbar not found")
        val actual = (view as MaterialToolbar).title?.toString()
        assert(actual == expected) { "Toolbar title was \"$actual\", expected \"$expected\"." }
    }
}
