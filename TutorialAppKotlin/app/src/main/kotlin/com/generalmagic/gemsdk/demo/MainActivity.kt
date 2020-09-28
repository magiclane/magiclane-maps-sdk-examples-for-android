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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.activities.BaseActivity
import com.generalmagic.gemsdk.demo.activities.searchaddress.SearchAddressActivity
import com.generalmagic.gemsdk.demo.controllers.*
import com.generalmagic.gemsdk.demo.util.GEMApplication
import com.generalmagic.gemsdk.demo.util.KeyboardUtil
import com.generalmagic.gemsdk.demo.util.MainMapStatusFollowingProvider
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.gemMapScreen
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getGlContext
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getMainActivity
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getMainMapView
import com.generalmagic.gemsdk.demo.util.StaticsHolder.Companion.getNavViewDrawerLayout
import com.generalmagic.gemsdk.demo.util.network.NetworkManager
import com.generalmagic.gemsdk.demo.util.network.NetworkProviderImpl
import com.generalmagic.gemsdk.mapview.GEMMapSurface
import com.generalmagic.gemsdk.models.ContentStoreItem
import com.generalmagic.gemsdk.models.Screen
import com.generalmagic.gemsdk.models.TContentType
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_layout.*
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import kotlinx.android.synthetic.main.tutorial_custom_nav.view.*
import kotlinx.android.synthetic.main.tutorial_custom_ptnav.view.*
import kotlinx.android.synthetic.main.tutorial_custom_sim.view.*
import kotlinx.android.synthetic.main.tutorial_custom_url.view.*
import kotlinx.android.synthetic.main.tutorial_drawercam.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_area.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_coords.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_instr.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_route.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_traffic.view.*
import kotlinx.android.synthetic.main.tutorial_hello.view.*
import kotlinx.android.synthetic.main.tutorial_multiplemaps.view.*
import kotlinx.android.synthetic.main.tutorial_predef_nav.view.*
import kotlinx.android.synthetic.main.tutorial_predef_ptnav.view.*
import kotlinx.android.synthetic.main.tutorial_predef_sim.view.*
import kotlinx.android.synthetic.main.tutorial_route_ab.view.*
import kotlinx.android.synthetic.main.tutorial_route_abc.view.*
import kotlinx.android.synthetic.main.tutorial_route_custom.view.*
import kotlinx.android.synthetic.main.tutorial_twotiledviews.view.*
import kotlinx.android.synthetic.main.tutorial_wiki.view.*
import java.net.Proxy
import java.util.*

class MainActivity : BaseActivity() {
    private var lastSelectedLayoutId: Int = R.id.tutorial_hello
    private var defaultMapStyle: ContentStoreItem? = null

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
        override fun onlineWorldMapSupportStatus(state: TStatus) {
            val listener = object : ProgressListener() {
                override fun notifyStatusChanged(status: Int) {
                    when (TContentUpdaterStatus.fromInt(status)) {
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
                ContentStore().createContentUpdater(TContentType.ECT_RoadMap.value, listener)
                    ?: return

            if (result.second == GEMError.KNoError.value) {
                mapUpdater = result.first ?: return
                mapUpdater?.update(true)
            }
        }
    }

    private val mapFollowListener = object : MainMapStatusFollowingProvider.Listener() {
        override fun statusChangedTo(following: Boolean) {
            runOnUiThread {
                currentController?.onMapFollowStatusChanged(following)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GEMMapSurface.appVariant = AppVariant.VARIANT_MAGICEARTH_BETA
        GEMApplication.appContext = applicationContext

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

        val followingProvider = MainMapStatusFollowingProvider.getInstance()
        followingProvider.listeners.add(mapFollowListener)

        gem_surface.onMainScreenCreated = { screen: Screen ->
            mainMapView =
                View(screen, TRectF(0.0f, 0.0f, 1.0f, 1.0f), mainMapViewListener)

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
                                        MainMapStatusFollowingProvider.getInstance().doFollow()
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

    private var currentController: AppLayoutController? = null
    private fun selectLayout(layoutId: Int) {
        val contentMain = main_container as ViewGroup
        contentMain.removeAllViews()
        toolbar_content.removeAllViews()
        appBarLayout.visibility = android.view.View.VISIBLE

        currentController = null

        KeyboardUtil.hideKeyboard(this)

        GEMSdkCall.execute {
            val prefs = mainMapView?.preferences() ?: return@execute

            prefs.routes()?.clear()
            defaultMapStyle?.let { prefs.setMapStyleById(it.getId()) }
        }

        GEMSdkCall.execute { MainMapStatusFollowingProvider.getInstance().doUnFollow() }
        bottomLeftButton?.visibility = android.view.View.INVISIBLE
        bottomCenterButton?.visibility = android.view.View.INVISIBLE
        bottomRightButton?.visibility = android.view.View.INVISIBLE

        lastSelectedLayoutId = layoutId
        when (layoutId) {
            R.id.tutorial_hello -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_hello,
                    contentMain
                ).helloViewController
            }

            R.id.tutorial_styles -> {
                val intent = Intent(this, StylesActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_twotiledViews -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_twotiledviews,
                    contentMain
                ).twoTiledViewsController
            }

            R.id.tutorial_multiple -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_multiplemaps,
                    contentMain
                ).multipleViewsController
            }

            R.id.tutorial_searchtext -> {
                val intent = Intent(this, SearchTextActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_searchnearby -> {
                val intent = Intent(this, SearchNearbyActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_searchpoi -> {
                val intent = Intent(this, SearchPoiActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_addresssearch -> {
                val intent = Intent(this, SearchAddressActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_route_ab -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_route_ab,
                    contentMain
                ).routeAbController
            }

            R.id.tutorial_route_abc -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_route_abc,
                    contentMain
                ).routeAbcController
            }

            R.id.tutorial_route_custom -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_route_custom,
                    contentMain
                ).routeCustomController
            }

            R.id.tutorial_predef_sim -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_predef_sim,
                    contentMain
                ).predefSimController
            }

            R.id.tutorial_predef_nav -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_predef_nav,
                    contentMain
                ).predefNavController
            }

            R.id.tutorial_predef_ptnav -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_predef_ptnav,
                    contentMain
                ).predefPtNavController
            }

            R.id.tutorial_custom_ptnav -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_custom_ptnav,
                    contentMain
                ).customPtNavController
            }

            R.id.tutorial_custom_sim -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_custom_sim,
                    contentMain
                ).customSimController
            }

            R.id.tutorial_custom_nav -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_custom_nav,
                    contentMain
                ).customNavController
            }

            R.id.tutorial_sensorsList -> {
                val intent = Intent(this, SensorsListActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_directcam -> {
                val intent = Intent(this, DirectCamActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_canvasDrawerCam -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_drawercam,
                    contentMain
                ).canvasDrawerCamController
            }

            R.id.tutorial_flyto_coords -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_flyto_coords,
                    contentMain
                ).flyToCoordsController
            }

            R.id.tutorial_flyto_area -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_flyto_area,
                    contentMain
                ).flyToAreaController
            }

            R.id.tutorial_flyto_instr -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_flyto_instr,
                    contentMain
                ).flyToInstrController
            }

            R.id.tutorial_flyto_route -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_flyto_route,
                    contentMain
                ).flyToRouteController
            }

            R.id.tutorial_flyto_traffic -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_flyto_traffic,
                    contentMain
                ).flyToTrafficController
            }

            R.id.tutorial_online_maps -> {
                val intent = Intent(this, OnlineMapsActivity::class.java)
                startActivity(intent)
            }

            R.id.tutorial_wiki -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_wiki,
                    contentMain
                ).wikiController
            }

            R.id.tutorial_custom_url -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_custom_url,
                    contentMain
                ).customServerController
            }

            else -> {
                /* NOTHING */
            }
        }

        GEMSdkCall.execute { currentController?.doStart() }
    }

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
