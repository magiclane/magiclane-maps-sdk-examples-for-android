/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.base

import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class SessionBase : Session(), DefaultLifecycleObserver {
    val context: CarContext
        get() = carContext

    var surfaceCallback: SurfaceCallback? = null

    val screenManager: ScreenManager
        get() = carContext.getCarService(ScreenManager::class.java)

    val appManager: AppManager
        get() = carContext.getCarService(AppManager::class.java)

    val navigationManager: NavigationManager
        get() = carContext.getCarService(NavigationManager::class.java)

    val carHardwareManager: CarHardwareManager
        get() = carContext.getCarService(CarHardwareManager::class.java)

    var isPaused = false
        private set

    // -----

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == CarContext.ACTION_NAVIGATE) {
            val uriString = intent.toUri(0)

            onNavigationRequested(uriString)
        }
    }

    override fun onCreateScreen(intent: Intent): Screen {
        lifecycle.addObserver(this)

        if (intent.action == CarContext.ACTION_NAVIGATE) {
            onNavigationRequested(intent.toUri(0))
        }

        return createMainScreen(intent)!!
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                (screenManager.top as? ScreenLifecycle)?.onBackPressed()
            }
        })

//        val permissions = arrayListOf(
//            CarAppPermission.ACCESS_SURFACE,
//            CarAppPermission.NAVIGATION_TEMPLATES,
//        )
//
//        carContext.requestPermissions(permissions) { _, denied ->
//            if (denied.size > 0)
//                context.finishCarApp()
//        }

        surfaceCallback = createSurfaceCallback(context)
        appManager.setSurfaceCallback(surfaceCallback)

        navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                this@SessionBase.onStopNavigation()
            }

            override fun onAutoDriveEnabled() {
            }
        })
    }

    override fun onPause(owner: LifecycleOwner) {
        isPaused = true
    }

    override fun onResume(owner: LifecycleOwner) {
        isPaused = false
    }

    // -----

    protected open fun createMainScreen(intent: Intent): Screen? = null

    protected open fun createSurfaceCallback(context: CarContext): SurfaceCallback? = null

    /**
     * @param uriString Is encoded as follows:
     *   1) "geo:12.345,14.8767" for a latitude, longitude pair.
     *   2) "geo:0,0?q=123+Main+St,+Seattle,+WA+98101" for an address.
     *   3) "geo:0,0?q=a+place+name" for a place to search for.
     */
    protected open fun onNavigationRequested(uriString: String) {}

    protected open fun onStopNavigation() {}
}
