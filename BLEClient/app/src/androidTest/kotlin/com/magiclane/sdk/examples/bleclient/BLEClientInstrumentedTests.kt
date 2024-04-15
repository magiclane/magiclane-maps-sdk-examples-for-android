/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.bleclient

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Resources.NotFoundException
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.polidea.rxandroidble2.mockrxandroidble.RxBleDeviceMock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class BLEClientInstrumentedTests
{

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    //private var navIdleResource: IdlingResource? = null

    private lateinit var activityRes: MainActivity
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    val permissionRuleVersionSAndUP = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    val permissionRuleVersionR = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    val permissionRuleVersionQ = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    val permissionRuleVersionQAndDown = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )


    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule =
        when
        {

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                permissionRuleVersionSAndUP

            Build.VERSION.SDK_INT == Build.VERSION_CODES.R ->
                permissionRuleVersionR

            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                permissionRuleVersionQ

            else -> permissionRuleVersionQAndDown
        }

    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        runBlocking { delay(2000) }
        activityScenarioRule.scenario.onActivity { activity ->
            activityRes = activity
        }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.close()
        activityRes.finish()
        //if (navIdleResource != null)
        //IdlingRegistry.getInstance().unregister(navIdleResource)
    }

    @Test
    fun checkBluetoothIsOn()
    {
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        assert(bluetoothAdapter != null) { "Bluetooth not supported on this device" }
        assert(bluetoothAdapter?.isEnabled == true) { "Bluetooth not activated" }
    }

    @Test
    fun checkListsOfDevicesContainsAtLeastOneConnectedDevice(): Unit = runBlocking {
        val unknownDeviceTxt = appContext.getString(R.string.unknown_device)
        delay(10000)
        onView(
            first(
                allOf(
                    withParent(withId(R.id.list_view)),
                    withChild(not(withText(unknownDeviceTxt)))
                )
            )
        ).check(
            matches(isDisplayed())
        ).withFailureHandler { error, _ ->
            throw NotFoundException("No devices found: ${error.message}")
        }
    }


    private fun <T> first(matcher: Matcher<T>): Matcher<T>
    {
        return object : BaseMatcher<T>()
        {
            var isFirst = true
            override fun matches(item: Any): Boolean
            {
                if (isFirst && matcher.matches(item))
                {
                    isFirst = false
                    return true
                }
                return false
            }

            override fun describeTo(description: Description)
            {
                description.appendText("should return first matching item")
            }
        }
    }
}