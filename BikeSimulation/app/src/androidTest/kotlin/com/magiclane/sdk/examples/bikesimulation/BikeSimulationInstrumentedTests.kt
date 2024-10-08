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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.bikesimulation.R
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
        Manifest.permission_group.LOCATION,
        Manifest.permission.INTERNET
    )
    
    @Rule
    @JvmField
    val chainRule: RuleChain = RuleChain.outerRule(activityScenarioRule).around(permissionRule)

    @Before
    fun checkTokenAndNetwork() {
        //verify token and internet connection
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun releaseScenario(){
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.moveToState(Lifecycle.State.DESTROYED)
    }
    
    @Test
    fun searchForDestination(): Unit = runBlocking {
        delay(10000)
        onView(withText(R.string.search_for_destination)).perform(click())
        onView(withText(R.string.search_for_destination)).perform(typeText("Bukingham"))
    }

    @Test
    fun searchForDestinationAndStartSim(): Unit = runBlocking {
        delay(10000)
        onView(withText(R.string.search_for_destination)).perform(click())
        onView(withText(R.string.search_for_destination)).perform(typeText("Bukingham"))
        onView(withSubstring("Buckingham Palace")).perform(click())
    }
}
