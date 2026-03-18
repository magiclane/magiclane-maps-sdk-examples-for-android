/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.EOffboardListenerStatus
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.SdkCall
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * JUnit TestRule that initializes the GEM SDK before tests.
 *
 * Handles: token validation, internet wait, SDK init, map data ready wait, cleanup.
 *
 * Usage:
 * ```
 * companion object {
 *     @get:ClassRule @JvmStatic
 *     val sdkRule = GemSdkTestRule()
 * }
 * ```
 *
 * For faster test suites, use `releaseAfter = false` to keep SDK alive across classes.
 */
class GemSdkTestRule(
    private val timeout: Long = DEFAULT_TIMEOUT,
    private val networkTimeout: Long = DEFAULT_NETWORK_TIMEOUT,
    private val releaseAfter: Boolean = true
) : TestRule {

    var isInitialized: Boolean = false
        private set

    override fun apply(base: Statement, description: Description): Statement =
        GemSdkStatement(base)

    private inner class GemSdkStatement(private val base: Statement) : Statement() {

        @Throws(Throwable::class)
        override fun evaluate() {
            val appContext: Context = ApplicationProvider.getApplicationContext()
            val weInitialized: Boolean

            if (!GemSdk.isInitialized()) {
                weInitialized = true

                // 1. Check token
                val token = GemSdk.getTokenFromManifest(appContext)
                if (token.isNullOrEmpty()) {
                    throw AssertionError(
                        """
                        |
                        |------------------------------------------------------------------
                        |No valid GEM SDK token found.
                        |
                        |${TestPrerequisites.TOKEN_SETUP_INSTRUCTIONS}
                        |------------------------------------------------------------------
                        |
                        """.trimMargin()
                    )
                }

                // 2. Wait for internet
                if (!NetworkUtils.awaitUsableInternet(appContext, networkTimeout)) {
                    throw AssertionError(
                        """
                        |
                        |------------------------------------------------------------------
                        |No usable internet after ${networkTimeout}ms.
                        |${NetworkUtils.getNetworkDiagnostics(appContext)}
                        |------------------------------------------------------------------
                        |
                        """.trimMargin()
                    )
                }

                // 3. Initialize SDK
                isInitialized = GemSdk.initSdkWithDefaults(appContext) == GemError.NoError
                if (!isInitialized) {
                    throw AssertionError(
                        """
                        |
                        |------------------------------------------------------------------
                        |GemSdk.initSdkWithDefaults() returned an error code.
                        |Token may be invalid or expired.
                        |Token: ${token.take(8)}...${token.takeLast(4)}
                        |------------------------------------------------------------------
                        |
                        """.trimMargin()
                    )
                }

                // 4. Wait for map data
                if (!awaitMapDataReady(timeout)) {
                    val diag = NetworkUtils.getNetworkDiagnostics(appContext)
                    val cause = if (!NetworkUtils.hasUsableInternet(appContext)) {
                        "Internet connection dropped.\n$diag"
                    } else {
                        "Map data not ready despite internet.\n$diag"
                    }
                    throw AssertionError(
                        """
                        |
                        |------------------------------------------------------------------
                        |SDK init timed out after ${timeout}ms.
                        |$cause
                        |------------------------------------------------------------------
                        |
                        """.trimMargin()
                    )
                }
            } else {
                weInitialized = false
                isInitialized = true
                if (!awaitMapDataReady(timeout)) {
                    throw AssertionError(
                        """
                        |
                        |------------------------------------------------------------------
                        |SDK initialized but map data not ready after ${timeout}ms.
                        |------------------------------------------------------------------
                        |
                        """.trimMargin()
                    )
                }
            }

            if (!SdkSettings.isMapDataReady) {
                throw Error(GemError.getMessage(GemError.OperationTimeout))
            }

            try {
                base.evaluate()
            } finally {
                if (weInitialized && releaseAfter) {
                    GemSdk.release()
                }
            }
        }

        private fun awaitMapDataReady(timeoutMs: Long): Boolean {
            var mapState = EOffboardListenerStatus.OldData
            val latch = CountDownLatch(1)

            SdkSettings.onWorldwideRoadMapSupportStatus = { state ->
                mapState = state
            }

            SdkCall.execute {
                ContentStore().checkForUpdate(EContentType.RoadMap)
            }

            try {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    latch.await(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    if (mapState == EOffboardListenerStatus.UpToDate) return true
                }
                return mapState == EOffboardListenerStatus.UpToDate
            } finally {
                latch.countDown()
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT = 600_000L
        const val DEFAULT_NETWORK_TIMEOUT = 90_000L
        private const val POLL_INTERVAL_MS = 500L
    }
}
