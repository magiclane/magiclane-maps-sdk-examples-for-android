/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("MemberVisibilityCanBePrivate", "unused", "RedundantOverride")

package com.generalmagic.sdk.examples.androidauto.androidAuto

import android.content.Intent
import android.view.Surface
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.LifecycleOwner
import com.generalmagic.sdk.androidauto.GemGlSurfaceAdapter
import com.generalmagic.sdk.androidauto.GemSurfaceContainerAdapter
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.core.Rect
import com.generalmagic.sdk.core.XyF
import com.generalmagic.sdk.d3scene.Animation
import com.generalmagic.sdk.d3scene.EAnimation.None
import com.generalmagic.sdk.examples.androidauto.androidAuto.base.SessionBase
import com.generalmagic.sdk.examples.androidauto.androidAuto.controllers.MainMenuController
import com.generalmagic.sdk.examples.androidauto.androidAuto.controllers.NavigationController
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.CarNavigationData
import com.generalmagic.sdk.examples.androidauto.androidAuto.screens.GemScreen
import com.generalmagic.sdk.examples.androidauto.androidAuto.screens.NavigationScreen
import com.generalmagic.sdk.examples.androidauto.androidAuto.util.CarAppNotifications
import com.generalmagic.sdk.examples.androidauto.androidAuto.util.CarNavigationDataFiller
import com.generalmagic.sdk.examples.androidauto.app.AndroidAutoService
import com.generalmagic.sdk.examples.androidauto.app.AppProcess
import com.generalmagic.sdk.examples.androidauto.services.NavigationInstance
import com.generalmagic.sdk.examples.androidauto.util.Util
import com.generalmagic.sdk.routesandnavigation.NavigationListener
import com.generalmagic.sdk.util.SdkCall

// -------------------------------------------------------------------------------------------------

class Service : CarAppService() {
    var surfaceAdapter: GemGlSurfaceAdapter? = null
        private set

    private var navigationData = CarNavigationData()
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
            val context = context?: return@onNavigationInstructionUpdated

            navigationData = CarNavigationData()
            SdkCall.execute {
                CarNavigationDataFiller.fillNavData(navigationData)
            }

            navigationData.getTrip(context)?.let {
                try {
                    session?.navigationManager?.updateTrip(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (session?.isPaused == true)
                CarAppNotifications.onNavigationDataUpdated(context, navigationData)

            (topScreen as? NavigationScreen)?.let {
                it.navigationData = navigationData
                it.invalidate()
            }
        }
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onCreateSession(): Session {
        return object : SessionBase() {
            init {
                NavigationInstance.listeners.add(navigationListener)
            }

            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)

                AppProcess.androidAutoService = AndroidAutoBridgeImpl()
                AppProcess.init(context)
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
                        surfaceAdapter?.visibleArea?.let {
                            onVisibleAreaChanged(it)
                        }
                    }
                }

                surfaceAdapter?.onVisibleAreaChanged = {
                    onVisibleAreaChanged(it)
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)

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

            override fun createMainScreen(intent: Intent): Screen = MainMenuController(context)

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

    private fun onVisibleAreaChanged(visibleArea: Rect) {
        SdkCall.execute {
            if (visibleArea.width == 0 || visibleArea.height == 0)
                return@execute

            val mapView = surfaceAdapter?.mapView

            val viewport = mapView?.viewport ?: return@execute
            val center = visibleArea.center ?: return@execute

            val x = center.x / viewport.width.toFloat()
            val y = center.y / viewport.height.toFloat()

            mapView.preferences?.followPositionPreferences?.cameraFocus = XyF(x, y)
        }
    }

    companion object {
        var instance: Service? = null

        val context: CarContext?
            get() = session?.context

        val session: SessionBase?
            get() = instance?.currentSession as? SessionBase

        val screenManager: ScreenManager?
            get() = session?.screenManager

        val topScreen: Screen?
            get() = screenManager?.top

        fun pushScreen(screen: Screen, popToRoot : Boolean = false) {
            if(popToRoot)
                pop(true)
            screenManager?.push(screen)
        }

        fun finish() {
            session?.context?.finishCarApp()
        }

        fun invalidateTop() {
            screenManager?.top?.invalidate()
        }

        fun pop(toRoot: Boolean = false) {
            if (screenManager?.stackSize == 1)
                return
            if (toRoot)
                screenManager?.popToRoot()
            else
                screenManager?.pop()
        }

        fun popToMap() {
            while (!((topScreen) as GemScreen).isMapVisible) {
                pop(false)
            }
        }

        fun showToast(text: String) {
            context?.let { context ->
                CarToast.makeText(context, text, CarToast.LENGTH_SHORT).show()
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
