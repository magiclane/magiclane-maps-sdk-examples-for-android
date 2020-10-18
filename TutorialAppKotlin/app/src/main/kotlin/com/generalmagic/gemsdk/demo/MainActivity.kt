/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.generalmagic.apihelper.EnumHelp
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.app.BaseActivity
import com.generalmagic.gemsdk.demo.app.GEMApplication
import com.generalmagic.gemsdk.demo.app.MapFollowingStatusProvider
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.gemMapScreen
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getGlContext
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getMainActivity
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getMainMapView
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getNavViewDrawerLayout
import com.generalmagic.gemsdk.demo.util.network.NetworkManager
import com.generalmagic.gemsdk.demo.util.network.NetworkProviderImpl
import com.generalmagic.gemsdk.mapview.GEMMapSurface
import com.generalmagic.gemsdk.models.Screen
import com.generalmagic.gemsdk.models.TContentType
import com.generalmagic.gemsdk.models.ViewListener
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.generalmagic.gemsdk.util.SDKPathsHelper
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_layout.*
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import java.io.File
import java.net.Proxy
import java.util.*

class MainActivity : BaseActivity() {
    private var mainMapView: View? = null
    private var bWasFollowingPosition = false
    private val mainMapViewListener = object : ViewListener() {}

    private val appNavMenuListener = NavigationView.OnNavigationItemSelectedListener { item ->
        // handle menu action
        selectLayout(item.itemId)
        drawer_layout?.closeDrawer(GravityCompat.START)
        true
    }

    private val networkProvider = NetworkProviderImpl()
    private lateinit var networkManager: NetworkManager

    private var mapUpdater: ContentUpdater? = null

    private val offboardListener = object : OffboardListener() {
        override fun onOnlineWorldMapSupportStatus(state: TStatus) {
            val listener = object : ProgressListener() {
                override fun notifyStatusChanged(status: Int) {
                    when (EnumHelp.fromInt<TContentUpdaterStatus>(status)) {
                        TContentUpdaterStatus.EFullyReady,
                        TContentUpdaterStatus.EPartiallyReady -> {
                            val updater = mapUpdater ?: return
                            // cancel routing
                            updater.apply()
                        }
                        else -> {
                        }
                    }
                }
            }

            val result =
                ContentStore().createContentUpdater(TContentType.ECT_RoadMap.value) ?: return

            if (result.second == GEMError.KNoError.value) {
                mapUpdater = result.first ?: return
                mapUpdater?.update(true, listener)
            }
        }
    }

    private val mapFollowListener = object : MapFollowingStatusProvider.Listener() {
        override fun statusChangedTo(following: Boolean) {
            runOnUiThread {
                currentController?.onMapFollowStatusChanged(following)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recordsDirName = "records"

        val iRecordsPath = StringBuilder().append(SDKPathsHelper.getPhonePath(this))
            .append(File.separator)
            .append(recordsDirName)

        val eRecordsPath = StringBuilder().append(SDKPathsHelper.getSdCardPath(this))
            .append(File.separator)
            .append(recordsDirName)

        GEMMapSurface.appVariant = AppVariant.VARIANT_MAGICEARTH_BETA
        GEMApplication.appContext = applicationContext
        GEMApplication.iRecordsPath = iRecordsPath.toString()
        GEMApplication.eRecordsPath = eRecordsPath.toString()
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        requestPermissions()

        // make status bar transparent
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        // no title
        supportActionBar?.title = ""

        nav_view.setNavigationItemSelectedListener(appNavMenuListener)

        supportActionBar?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.drawable.toolbar_invisible_surroundings)
        )

        // show menu indicator
        val drawerToggle: ActionBarDrawerToggle = object : ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        ) {}

        drawerToggle.isDrawerIndicatorEnabled = true
        drawerToggle.syncState()

        val followingProvider = MapFollowingStatusProvider.getInstance()
        followingProvider.listeners.add(mapFollowListener)

        gem_surface.onMainScreenCreated = { screen: Screen ->
            val mainViewRect = TRectF(0.0f, 0.0f, 1.0f, 1.0f)
            mainMapView = View.produce(screen, mainViewRect, mainMapViewListener)

            gem_surface.onPreHandleTouchListener = { _ ->
                bWasFollowingPosition = mainMapView?.isFollowingPosition() ?: false
                true
            }

            gem_surface.onPostHandleTouchListener = { _ ->
                if (bWasFollowingPosition && mainMapView?.isFollowingPosition() != true) {
                    GEMSdkCall.execute { followingProvider.noticePanInterruptFollow() }
                }
                true
            }

            val styles = ContentStore().getLocalContentList(TContentType.ECT_ViewStyleHighRes.value)
            styles?.let {
                for (style in styles) {
                    val name = style.getName()?.toLowerCase(Locale.getDefault()) ?: continue
                    if (name.contains("basic 1")) {
                        defaultMapStyle = style
                        break
                    }
                }
            }

            networkManager = NetworkManager(this)
            networkManager.onConnectionTypeChangedCallback = { type: NetworkManager.TConnectionType,
                proxyType: Proxy.Type, proxyHost: String, proxyPort: Int ->
                networkProvider.onNetworkConnectionTypeChanged(
                    type,
                    proxyType,
                    proxyHost,
                    proxyPort
                )
            }

            CommonSettings().setNetworkProvider(networkProvider)
            CommonSettings().setAllowConnection(true, offboardListener)

            runOnUiThread {
                mainMapView?.let {
                    if (!it.isFollowingPosition()) {
                        bottomButtons()?.let { buttons ->
                            buttons.bottomCenterButton?.visibility = android.view.View.GONE
                            buttons.bottomRightButton?.visibility = android.view.View.GONE
                            buttons.bottomLeftButton?.let { button ->
                                button.visibility = android.view.View.VISIBLE

                                button.backgroundTintList =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        resources.getColorStateList(R.color.colorPrimary, null)
                                    } else {
                                        @Suppress("deprecation")
                                        resources.getColorStateList(R.color.colorPrimary)
                                    }

                                button.setImageDrawable(
                                    resources.getDrawable(R.drawable.ic_gps_fixed_white_24dp, null)
                                )
                                button.setOnClickListener {
                                    GEMSdkCall.execute {
                                        MapFollowingStatusProvider.getInstance().doFollowStart()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        getMainMapView = { mainMapView }
        gemMapScreen = { gem_surface.getScreen() }
        getMainActivity = { this }
        getNavViewDrawerLayout = { drawer_layout }
        getGlContext = { gem_surface.getGlContext() }

        /*DEFAULT*/
        nav_view.setCheckedItem(R.id.tutorial_hello)
        nav_view.menu.performIdentifierAction(R.id.tutorial_hello, 0)
    }

    override fun onPause() {
        super.onPause()
        gem_surface.onPause()
    }

    override fun onResume() {
        super.onResume()
        gem_surface.onResume()
    }

    override fun onBackPressed() {
        if (currentController?.onBackPressed() == true) {
            return
        }

        terminateApp()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsFinish(granted: Boolean) {
        if (!granted) {
            terminateApp()
        }
    }

    private fun requestPermissions(): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.BLUETOOTH
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        return requestPermissions(permissions.toTypedArray())
    }

    fun showAppBar() {
        supportActionBar?.show()
        drawer_layout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    fun hideAppBar() {
        supportActionBar?.hide()
        drawer_layout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    fun bottomButtons(): ConstraintLayout? {
        return bottomButtons
    }
}
