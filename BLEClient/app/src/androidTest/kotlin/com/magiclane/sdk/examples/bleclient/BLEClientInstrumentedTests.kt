/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bleclient

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class BLEClientInstrumentedTests {

    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

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
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )
    private val permissionRuleVersionQ = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private val permissionRuleVersionQAndDown = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private val permissionRule: () -> GrantPermissionRule = {
        when {
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
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        activityScenarioRule.scenario.onActivity { activity ->
            activity.finish()
        }
        activityScenarioRule.scenario.close()
    }

    private fun isEmulator(): Boolean {
        return (
            Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.BOARD == "QC_Reference_Phone" ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HOST.startsWith("Build") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("sdk_gphone") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu")
            )
    }

    @Test
    fun checkBluetoothIsOn(): Unit = runBlocking {
        if (isEmulator()) return@runBlocking // Skip on emulator - no Bluetooth hardware
        delay(3000)
        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        assert(bluetoothAdapter != null) { "Bluetooth not supported on this device" }
        assert(bluetoothAdapter?.isEnabled == true) { "Bluetooth not activated" }
    }
}
