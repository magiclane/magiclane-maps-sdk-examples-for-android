/*
 * SPDX-FileCopyrightText: 2025-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.magiclane.sdk.core.GemSdk

/** Utility for common test prerequisites checks. */
object TestPrerequisites {

    /** Instructions for setting the GEM_TOKEN, used in error messages. */
    const val TOKEN_SETUP_INSTRUCTIONS = """Set GEM_TOKEN in one of these locations:
- Environment variable: GEM_TOKEN=your_token
- Global gradle.properties:
  * Linux/Mac: ~/.gradle/gradle.properties
  * Windows: %USERPROFILE%\.gradle\gradle.properties
- Project gradle.properties

Get your token from: https://developer.magiclane.com"""

    /** Verifies that a valid GEM SDK token is present in the manifest. */
    fun assertValidToken(context: Context? = null) {
        val appContext = context ?: ApplicationProvider.getApplicationContext()
        val token = GemSdk.getTokenFromManifest(appContext)
        if (token.isNullOrEmpty()) {
            throw AssertionError(
                """
                |
                |------------------------------------------------------------------
                |No valid GEM SDK token found.
                |
                |$TOKEN_SETUP_INSTRUCTIONS
                |------------------------------------------------------------------
                |
                """.trimMargin()
            )
        }
    }

    /** Verifies that usable internet connectivity is available. */
    fun assertInternetAvailable(
        context: Context? = null,
        timeoutMs: Long = NetworkUtils.DEFAULT_INTERNET_TIMEOUT_MS
    ) {
        val appContext = context ?: ApplicationProvider.getApplicationContext()
        NetworkUtils.assertInternetAvailable(appContext, timeoutMs)
    }

    /** Verifies both token validity and internet connectivity. */
    fun assertTokenAndNetwork(
        context: Context? = null,
        networkTimeoutMs: Long = NetworkUtils.DEFAULT_INTERNET_TIMEOUT_MS
    ) {
        assertValidToken(context)
        assertInternetAvailable(context, networkTimeoutMs)
    }

    /** Verifies that the GEM SDK has been initialized. */
    fun assertSdkInitialized(sdkRule: GemSdkTestRule? = null) {
        val initialized = sdkRule?.isInitialized ?: GemSdk.isInitialized()
        if (!initialized) {
            throw AssertionError(
                """
                |
                |------------------------------------------------------------------
                |GEM SDK has not been initialized.
                |Ensure GemSdkTestRule is configured as @ClassRule.
                |------------------------------------------------------------------
                |
                """.trimMargin()
            )
        }
    }
}
