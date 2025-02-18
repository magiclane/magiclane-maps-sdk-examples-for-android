/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidauto.androidAuto.base

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// -------------------------------------------------------------------------------------------------

abstract class ScreenLifecycle(context: CarContext) : Screen(context) {
    val context = carContext

    val lifecycleState: Lifecycle.State
        get() = lifecycle.currentState

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> onCreate()
            Lifecycle.Event.ON_START -> onStart()
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_DESTROY -> onDestroy()
            Lifecycle.Event.ON_ANY -> onLifecycleEventChanged()
        }
    }

    init {
        lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * Called only by the Session!
     */
    internal open fun onBackPressed() {}

    protected open fun onCreate() {}
    protected open fun onStart() {}
    protected open fun onResume() {}
    protected open fun onPause() {}
    protected open fun onStop() {}
    protected open fun onDestroy() {}
    protected open fun onLifecycleEventChanged() {}
}

// -------------------------------------------------------------------------------------------------