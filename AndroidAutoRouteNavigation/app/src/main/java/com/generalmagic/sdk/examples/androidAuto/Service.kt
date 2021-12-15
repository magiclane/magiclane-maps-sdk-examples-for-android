/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused", "RedundantOverride")

package com.generalmagic.sdk.examples.androidAuto

import android.view.Surface
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.validation.HostValidator
import com.generalmagic.sdk.androidauto.GemGlSurfaceAdapter
import com.generalmagic.sdk.androidauto.GemSurfaceContainerAdapter
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.Rect
import com.generalmagic.sdk.core.XyF
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation.None
import com.generalmagic.sdk.examples.androidAuto.base.SessionBase
import com.generalmagic.sdk.examples.androidAuto.controllers.MainMenuController
import com.generalmagic.sdk.examples.androidAuto.controllers.NavigationController
import com.generalmagic.sdk.examples.androidAuto.controllers.RoutesPreviewController
import com.generalmagic.sdk.examples.androidAuto.screens.GemScreen
import com.generalmagic.sdk.examples.androidAuto.screens.NavigationScreen
import com.generalmagic.sdk.examples.app.AndroidAutoService
import com.generalmagic.sdk.examples.app.AppProcess
import com.generalmagic.sdk.examples.services.NavigationInstance
import com.generalmagic.sdk.examples.util.Util
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------

class Service : CarAppService() {
    var surfaceAdapter: GemGlSurfaceAdapter? = null
        private set

    private val navigationListener = NavigationListener.create(
        onNavigationStarted = onNavigationStarted@{
            val session = session ?: return@onNavigationStarted

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
            if (!GemError.isError(it))
                return@onNavigationError
            screenManager?.popToRoot()
        },
        onNavigationInstructionUpdated = onNavigationInstructionUpdated@{
            (screenManager?.top as? NavigationScreen)?.invalidate()
        }
    )

// -------------------------------------------------------------------------------------------------

    override fun onCreateSession(): Session {
        return object : SessionBase() {
            init {
                NavigationInstance.listeners.add(navigationListener)
            }

            override fun onCreate() {
                super.onCreate()

                AppProcess.androidAutoService = getProcessBridge()
                AppProcess.init(context)
                AppProcess.onAndroidAutoConnected()

                surfaceAdapter = GemGlSurfaceAdapter(context)
                surfaceAdapter?.onDefaultMapViewCreated = { mapView ->
                    mapView.onEnterFollowingPosition = {
                        (screenManager.top as? GemScreen)?.onMapFollowChanged(true)
                    }
                    mapView.onExitFollowingPosition = {
                        (screenManager.top as? GemScreen)?.onMapFollowChanged(false)
                    }

                    SdkCall.postAsync {
                        mapView.followPosition(true, Animation(None))
                        surfaceAdapter?.visibleArea?.let {
                            onVisibleAreaChanged(it)
                        }
                    }
                }

                surfaceAdapter?.onVisibleAreaChanged = {
                    onVisibleAreaChanged(it)
                }
            }

            override fun onDestroy() {
                super.onDestroy()

                AppProcess.onAndroidAutoDisconnected()
                AppProcess.androidAutoService = AndroidAutoService.empty

                surfaceAdapter?.release()
                surfaceAdapter = null
            }

            override fun createSurfaceCallback(context: CarContext): SurfaceCallback {
                return object : SurfaceCallback {
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
            }

            override fun createMainScreen(): Screen = MainMenuController(context)

            override fun onStopNavigation() {
                NavigationInstance.stopNavigation()
            }

            override fun onNavigationRequested(uriString: String) {
                if (Util.isGeoIntent(uriString))
                    AppProcess.handleGeoUri(uriString)
            }
        }
    }

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

// -------------------------------------------------------------------------------------------------

    private fun onVisibleAreaChanged(visibleArea: Rect) {
        SdkCall.execute {
            if (visibleArea.width == 0 || visibleArea.height == 0)
                return@execute

            val mapView = surfaceAdapter?.mapView

            val viewport = mapView?.viewport ?: return@execute
            val center = visibleArea.center ?: return@execute

            val x = center.x / viewport.width.toFloat()
            val y = center.y / viewport.height.toFloat()

            mapView.preferences?.followPositionCameraFocus = XyF(x, y)
        }
    }

    private fun getProcessBridge(): AndroidAutoService {
        return object : AndroidAutoService() {
            override fun finish() {
                session?.context?.finishCarApp()
            }

            override fun invalidate() {
                invalidateTop()
            }

            override fun showRoutesPreview(landmark: Landmark) {
                val context = session?.context ?: return

                show(RoutesPreviewController(context, landmark))
            }

            fun popToRoot() {
                screenManager?.popToRoot()
            }
        }
    }

    companion object {
        var instance: Service? = null

        val session: SessionBase?
            get() = instance?.currentSession as? SessionBase

        val context: CarContext?
            get() = session?.context

        val screenManager: ScreenManager?
            get() = session?.screenManager

        fun finish() {
            context?.finishCarApp()
        }

        fun invalidateTop() {
            instance?.surfaceAdapter?.visibleArea?.let {
                instance?.onVisibleAreaChanged(it)
            }
            screenManager?.top?.invalidate()
        }

        fun show(screen: GemScreen) {
            if (screen.mustPopToRoot) {
                screenManager?.popToRoot()
                screenManager?.push(screen)
            } else screenManager?.push(screen)
        }

        fun pop(toRoot: Boolean = false) {
            if (toRoot)
                screenManager?.popToRoot()
            else
                screenManager?.pop()
        }
    }
}

// -------------------------------------------------------------------------------------------------
