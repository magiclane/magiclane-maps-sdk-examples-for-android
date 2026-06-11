/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.searchcompose

import androidx.test.espresso.idling.CountingIdlingResource

// Allows Espresso instrumented tests to wait for asynchronous SDK operations
// (SDK initialisation, search requests) before asserting UI state.
object EspressoIdlingResource {
    private const val RESOURCE_NAME = "SearchIdlingResource"

    val espressoIdlingResource = CountingIdlingResource(RESOURCE_NAME)

    fun increment() = espressoIdlingResource.increment()

    fun decrement() {
        if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement()
    }
}
