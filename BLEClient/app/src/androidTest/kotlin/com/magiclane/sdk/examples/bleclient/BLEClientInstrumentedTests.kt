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
import android.net.ConnectivityManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class BLEClientInstrumentedTests
{

    private val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)
    
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val permissionRuleVersionSAndUP = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private val permissionRuleVersionR = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    private val permissionRuleVersionQ = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private val permissionRuleVersionQAndDown = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private val permissionRule: () -> GrantPermissionRule = {
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
    }

    @Rule
    @JvmField
    val chainRule = RuleChain.outerRule(permissionRule()).around(activityScenarioRule)
    
    @Before
    fun registerIdlingResource()
    {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        activityScenarioRule.scenario.onActivity { activity ->
            activity.finish()
        }
        activityScenarioRule.scenario.close()
    }

    @Test
    fun checkBluetoothIsOn(): Unit = runBlocking {
        delay(3000)
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        assert(bluetoothAdapter != null) { "Bluetooth not supported on this device" }
        assert(bluetoothAdapter?.isEnabled == true) { "Bluetooth not activated" }
    }
}
