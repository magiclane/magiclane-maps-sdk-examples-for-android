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


package com.magiclane.sdk.examples.bikesimulation

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withParentIndex
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
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
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class BikeSimulationInstrumentedTests {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun Int.sToMills() = this * 1000L
    private val GM_LAT: Double = 45.651165
    private val GM_LON: Double = 25.604826

    @Rule
    @JvmField
    val chainRule: RuleChain = RuleChain.outerRule(permissionRule).around(activityScenarioRule)

    @Before
    fun checkTokenAndNetwork() {
        //verify token and internet connection
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun releaseScenario() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun searchForDestination(): Unit = runBlocking {
        delay(10.sToMills())
        onView(withHint(R.string.search_for_destination)).perform(click())
        onView(withHint(R.string.search)).perform(typeText("Buckingham"))
        delay(10.sToMills())
    }

    @Test
    fun searchForDestinationAndStartSim(): Unit = runBlocking {
        delay(10.sToMills())
        onView(withHint(R.string.search_for_destination)).perform(click())
        onView(withHint(R.string.search)).perform(typeText("Buckingham"))
        delay(12.sToMills())
        onView(allOf(withChild(withSubstring("Buckingham Palace")), withParentIndex(0))).perform(click())
        delay(2.sToMills())
        onView(withId(R.id.start_simulation)).perform(click())
        delay(10.sToMills())
    }

    @Test
    fun touchLandmarkAndStartSim(): Unit = runBlocking {
        delay(10.sToMills())
        onView(withId(R.id.gem_surface)).perform(CenterAndTouch(GM_LAT, GM_LON))
        delay(2.sToMills())
        onView(withId(R.id.start_simulation)).perform(click())
        delay(10.sToMills())
    }

    @Test
    fun changeBikeSettingsToMaxAndStartSim(): Unit = runBlocking {
        delay(10.sToMills())
        onView(withId(R.id.bike_settings_button)).perform(click())
        delay(5.sToMills())
        onView(allOf(withClassName(`is`(MaterialSwitch::class.qualifiedName)), withParent(withChild(withSubstring("E-Bike"))))).perform(click())
        onView(allOf(withClassName(`is`(MaterialSwitch::class.qualifiedName)), withParent(withChild(withSubstring("Avoid Ferries"))))).perform(click())
        onView(allOf(withClassName(`is`(MaterialSwitch::class.qualifiedName)), withParent(withChild(withSubstring("Avoid Unpaved Roads"))))).perform(click())
        onView(allOf(withChild(withSubstring("Hills")), isDisplayed())).perform(SetSettingsSlider(10f))
        onView(allOf(withChild(withSubstring("Bike Weight")), isDisplayed())).perform(SetSettingsSlider(50f))
        onView(allOf(withChild(withSubstring("Biker Weight")), isDisplayed())).perform(SetSettingsSlider(150f))
        onView(allOf(withChild(withSubstring("Aux Consumption Day")), isDisplayed())).perform(SetSettingsSlider(100f))
        onView(allOf(withChild(withSubstring("Aux Consumption Night")), isDisplayed())).perform(SetSettingsSlider(100f))
        onView(withId(R.id.bike_settings_toolbar)).perform(ViewActions.pressBack())
        delay(2.sToMills())
        onView(withId(R.id.gem_surface)).perform(CenterAndTouch(GM_LAT, GM_LON))
        delay(2.sToMills())
        onView(withId(R.id.start_simulation)).perform(click())
        delay(10.sToMills())
    }

    @Test
    fun changeBikeSettingsToMinAndStartSim(): Unit = runBlocking {
        delay(10.sToMills())
        onView(withId(R.id.bike_settings_button)).perform(click())
        delay(2.sToMills())
        onView(allOf(withClassName(`is`(MaterialSwitch::class.qualifiedName)), withParent(withChild(withSubstring("E-Bike"))))).perform(click())
        onView(allOf(withClassName(`is`(MaterialSwitch::class.qualifiedName)), withParent(withChild(withSubstring("Avoid Ferries"))))).perform(click())
        onView(allOf(withClassName(`is`(MaterialSwitch::class.qualifiedName)), withParent(withChild(withSubstring("Avoid Unpaved Roads"))))).perform(click())
        onView(allOf(withChild(withSubstring("Hills")), isDisplayed())).perform(SetSettingsSlider(0f))
        onView(allOf(withChild(withSubstring("Bike Weight")), isDisplayed())).perform(SetSettingsSlider(9f))
        onView(allOf(withChild(withSubstring("Biker Weight")), isDisplayed())).perform(SetSettingsSlider(10f))
        onView(allOf(withChild(withSubstring("Aux Consumption Day")), isDisplayed())).perform(SetSettingsSlider(0f))
        onView(allOf(withChild(withSubstring("Aux Consumption Night")), isDisplayed())).perform(SetSettingsSlider(0f))
        onView(withId(R.id.bike_settings_toolbar)).perform(ViewActions.pressBack())
        delay(2.sToMills())
        onView(withId(R.id.gem_surface)).perform(CenterAndTouch(GM_LAT, GM_LON))
        delay(2.sToMills())
        onView(withId(R.id.start_simulation)).perform(click())
        delay(10.sToMills())
    }
    
    class SetSettingsSlider(private val value: Float) : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isDisplayed()
        }

        override fun getDescription(): String {
            return "Set Settings Slider with value : $value"
        }

        override fun perform(uiController: UiController?, view: View?) {
            view?.let {
                val slider = it.findViewById<Slider>(R.id.item_slider)
                slider.value = value
            } ?: throw IllegalArgumentException()
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

