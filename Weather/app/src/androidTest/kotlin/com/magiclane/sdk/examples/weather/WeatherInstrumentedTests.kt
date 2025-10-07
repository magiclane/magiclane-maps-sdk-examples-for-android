// -------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

// -------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.weather

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4ClassRunner::class)
@LargeTest
class WeatherInstrumentedTests
{
    private val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @Rule
    @JvmField
    val activityRule: RuleChain = RuleChain.outerRule(permissionRule).around(activityScenarioRule)

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private lateinit var activityRes: MainActivity

    @Before
    fun startActivity()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        IdlingPolicies.setIdlingResourceTimeout(1, TimeUnit.MINUTES)
        checkTokenAndNetwork()
    }

    @After
    fun closeActivity()
    {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.onActivity {
            it.finish()
        }
        activityScenarioRule.scenario.close()
    }

    private fun checkTokenAndNetwork()
    {
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }

    }

    @Test
    fun checkCurrentForecastViews(): Unit = runBlocking {
        val channel = Channel<Unit>()
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
        var x: Int
        var y: Int
        val surface = activityRes.run {
            x = resources.displayMetrics.widthPixels / 2
            y = resources.displayMetrics.heightPixels / 2
            y -= obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionBarSize)).getDimensionPixelSize(0, -1) / 2
            val view = findViewById<GemSurfaceView>(R.id.gem_surface_view)
            SdkCall.execute {
                view.mapView?.centerOnCoordinates(Coordinates(45.6511659375, 25.6048215625), animation = Animation(EAnimation.None))
                //(725,1398)
                launch {
                    delay(1000)
                    channel.send(Unit)
                }
            }
            view
        }

        launch {
            channel.receive()
            delay(1000)
            SdkCall.execute { surface.mapView?.onTouch?.let { it(Xy(x, y)) } }
            delay(1000)
            onView(withId(R.id.forecast_button)).perform(click())
            onView(withId(R.id.current_forecast_card)).check(matches(isDisplayed()))
            onView(withId(R.id.current_forecast_container)).check(matches(isDisplayed()))
            onView(withId(R.id.feels_like)).check(matches(isDisplayed()))
            onView(withId(R.id.location_name)).check(matches(isDisplayed()))
            onView(withId(R.id.description)).check(matches(isDisplayed()))
            onView(withId(R.id.current_temperature)).check(matches(isDisplayed()))
            onView(withId(R.id.local_time)).check(matches(isDisplayed()))
            onView(withId(R.id.updated_at)).check(matches(isDisplayed()))
            onView(withId(R.id.updated_at)).check(matches(isDisplayed()))
            onView(withId(R.id.forecast_list)).check(
                WeatherRecyclerMatches(
                    HasWeatherItems(
                        mutableListOf(
                            "AirQuality",
                            "DewPoint",
                            "Humidity",
                            "Pressure",
                            "Sunrise",
                            "Sunset",
                            "UV",
                            "Visibility",
                            "WindDirection",
                            "WindSpeed",
                        )
                    )
                )
            )
        }
    }

    @Test
    fun checkDailyForecastViews(): Unit = runBlocking {
        val channel = Channel<Unit>()
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
        var x: Int
        var y: Int
        val surface = activityRes.run {
            x = resources.displayMetrics.widthPixels / 2
            y = resources.displayMetrics.heightPixels / 2
            y -= obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionBarSize)).getDimensionPixelSize(0, -1) / 2
            val view = findViewById<GemSurfaceView>(R.id.gem_surface_view)
            SdkCall.execute {
                view.mapView?.centerOnCoordinates(Coordinates(45.6511659375, 25.6048215625), animation = Animation(EAnimation.None))
                launch {
                    delay(1000)
                    channel.send(Unit)
                }
            }
            view
        }

        launch {
            channel.receive()
            delay(1000)
            SdkCall.execute { surface.mapView?.onTouch?.let { it(Xy(x, y)) } }
            delay(1000)
            onView(withId(R.id.daily_forecast_button)).perform(click())
            onView(withId(R.id.forecast_list)).check(matches(isDisplayed()))
            onView(withId(R.id.forecast_list)).check(
                WeatherRecyclerMatches(
                    HasWeatherItems(
                        mutableListOf(
                            "MONDAY",
                            "TUESDAY",
                            "WEDNESDAY",
                            "THURSDAY",
                            "FRIDAY",
                            "SATURDAY",
                            "SUNDAY"
                        )
                    )
                )
            )
        }
    }

    @Test
    fun checkHourlyForecastViews(): Unit = runBlocking {
        val channel = Channel<Unit>()
        onView(withId(R.id.gem_surface_view)).check(matches(isDisplayed()))
        var x: Int
        var y: Int
        val surface = activityRes.run {
            x = resources.displayMetrics.widthPixels / 2
            y = resources.displayMetrics.heightPixels / 2
            y -= obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.actionBarSize)).getDimensionPixelSize(0, -1) / 2
            val view = findViewById<GemSurfaceView>(R.id.gem_surface_view)
            SdkCall.execute {
                view.mapView?.centerOnCoordinates(Coordinates(45.6511659375, 25.6048215625), animation = Animation(EAnimation.None))
                launch {
                    delay(1000)
                    channel.send(Unit)
                }
            }
            view
        }

        launch {
            channel.receive()
            delay(1000)
            SdkCall.execute { surface.mapView?.onTouch?.let { it(Xy(x, y)) } }
            delay(1000)
            onView(withId(R.id.hourly_forecast_button)).perform(click())
            onView(withId(R.id.forecast_list)).check(matches(isDisplayed()))

        }
    }

    class HasWeatherItems(private val withValues: MutableList<String>) : TypeSafeMatcher<ForecastItem>()
    {
        override fun describeTo(description: Description?)
        {
            description?.appendText("Could not find ForecastItem with any of the values: $withValues")
        }

        override fun matchesSafely(item: ForecastItem?): Boolean
        {
            return item!!.conditionValue in withValues || item.conditionName in withValues || item.dayOfWeek in withValues
        }

    }

    class WeatherRecyclerMatches(private val matcher: HasWeatherItems) : ViewAssertion
    {

        override fun check(view: View?, noViewFoundException: NoMatchingViewException?)
        {
            view?.let {
                val recyclerView = view as RecyclerView
                val nResults = recyclerView.adapter?.itemCount
                if (nResults != null)
                    assert(nResults > 0) { "Empty adapter." }
                else
                    throw Error("No adapter attached!")
                (recyclerView.adapter as ListAdapter<*, *>).currentList.forEach {
                    assert(matcher.matches(it))
                }
            } ?: noViewFoundException
        }
    }
}
