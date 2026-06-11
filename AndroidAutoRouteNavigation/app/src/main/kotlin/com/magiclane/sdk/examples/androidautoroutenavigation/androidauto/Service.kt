/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused", "RedundantOverride")

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto

import android.content.Intent
import android.view.Surface
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.SessionInfo.DISPLAY_TYPE_MAIN
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.LifecycleOwner
import com.magiclane.sdk.androidauto.GemGlSurfaceAdapter
import com.magiclane.sdk.androidauto.GemSurfaceContainerAdapter
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.XyF
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation.None
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.base.SessionBase
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers.MainMenuController
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers.NavigationController
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers.RoutesPreviewController
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.CarNavigationData
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.GemScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.NavigationScreen
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.util.CarAppNotifications
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.util.CarNavigationDataFiller
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AndroidAutoService
import com.magiclane.sdk.examples.androidautoroutenavigation.app.AppProcess
import com.magiclane.sdk.examples.androidautoroutenavigation.services.NavigationInstance
import com.magiclane.sdk.examples.androidautoroutenavigation.util.Util
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.util.SdkCall

class Service : CarAppService() {
    var surfaceAdapter: GemGlSurfaceAdapter? = null
        private set

    private var navigationData = CarNavigationData()

    // Listens to navigation events and keeps the car screen in sync with the navigation state.
    private val navigationListener = NavigationListener.create(
        onNavigationStarted = onNavigationStarted@{
            val session = session ?: return@onNavigationStarted

            // If NavigationController is already on top, notify it; otherwise push a new one.
            val navController = session.screenManager.top as? NavigationController
            if (navController == null) {
                session.screenManager.push(NavigationController(session.context, null))
            } else {
                navController.onNavigationStarted()
                navController.invalidate()
            }
        },
        onDestinationReached = {
            screenManager?.popToRoot()
        },
        onNavigationError = onNavigationError@{
            if (!GemError.isError(it)) {
                return@onNavigationError
            }
            screenManager?.popToRoot()
        },
        onNavigationInstructionUpdated = onNavigationInstructionUpdated@{
            val context = context ?: return@onNavigationInstructionUpdated

            // Rebuild navigation data from the latest instruction.
            navigationData = CarNavigationData()
            SdkCall.execute {
                CarNavigationDataFiller.fillNavData(navigationData)
            }

            // Update the NavigationManager trip so the OS keeps its navigation state current.
            navigationData.getTrip(context)?.let {
                try {
                    session?.navigationManager?.updateTrip(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // When the car screen is paused (e.g. app in background), post a notification instead.
            if (session?.isPaused == true) {
                CarAppNotifications.onNavigationDataUpdated(context, navigationData)
            }

            (topScreen as? NavigationScreen)?.let {
                it.navigationData = navigationData
                it.invalidate()
            }
        },
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onCreateSession(): Session = MainSession()

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    // Adjusts the map camera focus point whenever the visible area changes (e.g. panel shown/hidden).
    private fun onVisibleAreaChanged(visibleArea: Rect) {
        SdkCall.execute {
            if (visibleArea.width == 0 || visibleArea.height == 0) {
                return@execute
            }

            val mapView = surfaceAdapter?.mapView
            val viewport = mapView?.viewport ?: return@execute
            val center = visibleArea.center ?: return@execute

            // Shift the follow-position camera focus towards the bottom of the visible area.
            val x = center.x / viewport.width.toFloat()
            val y = (visibleArea.bottom * 0.75f) / viewport.height.toFloat()

            mapView.preferences?.followPositionPreferences?.cameraFocus = XyF(x, y)
        }

        (topScreen as? RoutesPreviewController)?.updateMapView()
    }

    // Named inner class for the Android Auto session, replacing the anonymous object for clarity.
    private inner class MainSession : SessionBase() {
        init {
            NavigationInstance.listeners.add(navigationListener)
        }

        override fun onCreate(owner: LifecycleOwner) {
            super.onCreate(owner)

            AppProcess.androidAutoService = AndroidAutoBridgeImpl()
            val errorCode = AppProcess.init(context)
            if (errorCode == GemError.NoError) {
                AppProcess.onAndroidAutoConnected()

                surfaceAdapter = GemGlSurfaceAdapter(context)
                surfaceAdapter?.onDefaultMapViewCreated = { mapView ->
                    mapView.onEnterFollowingPosition = {
                        (topScreen as? GemScreen)?.onMapFollowChanged(true)
                    }
                    mapView.onExitFollowingPosition = {
                        (topScreen as? GemScreen)?.onMapFollowChanged(false)
                    }

                    SdkCall.postAsync {
                        mapView.followPosition(true, Animation(None))
                        surfaceAdapter?.visibleArea?.let { onVisibleAreaChanged(it) }
                    }
                }

                surfaceAdapter?.onVisibleAreaChanged = { onVisibleAreaChanged(it) }
            } else {
                CarToast.makeText(
                    context,
                    context.getString(
                        R.string.sdk_initialization_failed,
                        SdkCall.runSynced { GemError.getMessage(errorCode, context) },
                    ),
                    CarToast.LENGTH_LONG,
                ).show()
                AppProcess.androidAutoService.finish()
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)

            AppProcess.onAndroidAutoDisconnected()
            AppProcess.androidAutoService = AndroidAutoService.empty

            surfaceAdapter?.release()
            surfaceAdapter = null
        }

        override fun createSurfaceCallback(context: CarContext): SurfaceCallback = MapSurfaceCallback()

        override fun createMainScreen(intent: Intent): Screen = MainMenuController(context)

        // Called when the user taps the "×" button on the bottom navigation panel.
        // Must mirror onBackPressed() in NavigationController: stop navigation and return to root.
        override fun onStopNavigation() {
            NavigationInstance.stopNavigation()
            screenManager.popToRoot()
        }

        override fun onNavigationRequested(uriString: String) {
            if (Util.isGeoIntent(uriString)) {
                AppProcess.handleGeoUri(uriString)
            }
        }
    }

    // Delegates all surface lifecycle events to the GemGlSurfaceAdapter.
    private inner class MapSurfaceCallback : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surfaceAdapter?.onSurfaceAvailable(object : GemSurfaceContainerAdapter() {
                override fun getHeight(): Int = surfaceContainer.height
                override fun getWidth(): Int = surfaceContainer.width
                override fun getDpi(): Int = surfaceContainer.dpi
                override fun getSurface(): Surface? = surfaceContainer.surface
            })
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surfaceAdapter?.onSurfaceDestroyed()
        }

        override fun onStableAreaChanged(stableArea: android.graphics.Rect) {
            surfaceAdapter?.onStableAreaChanged(stableArea)
        }

        override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
            surfaceAdapter?.onVisibleAreaChanged(visibleArea)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            surfaceAdapter?.onFling(velocityX, velocityY)
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            surfaceAdapter?.onScale(focusX, focusY, scaleFactor)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            surfaceAdapter?.onScroll(distanceX, distanceY)
        }
    }

    companion object {
        var instance: Service? = null

        val context: CarContext?
            get() = session?.context

        val session: SessionBase?
            get() = instance?.getSession(SessionInfo(DISPLAY_TYPE_MAIN, "main")) as? SessionBase

        val screenManager: ScreenManager?
            get() = session?.screenManager

        val topScreen: Screen?
            get() = screenManager?.top

        fun pushScreen(screen: Screen, popToRoot: Boolean = false) {
            if (popToRoot) {
                pop(true)
            }
            screenManager?.push(screen)
        }

        fun finish() {
            session?.context?.finishCarApp()
        }

        fun invalidateTop() {
            screenManager?.top?.invalidate()
        }

        fun pop(toRoot: Boolean = false) {
            if (screenManager?.stackSize == 1) {
                return
            }
            if (toRoot) {
                screenManager?.popToRoot()
            } else {
                screenManager?.pop()
            }
        }

        fun popToMap() {
            while (!(topScreen as GemScreen).isMapVisible) {
                pop(false)
            }
        }

        fun showToast(text: String) {
            context?.let { CarToast.makeText(it, text, CarToast.LENGTH_SHORT).show() }
        }
    }
}
