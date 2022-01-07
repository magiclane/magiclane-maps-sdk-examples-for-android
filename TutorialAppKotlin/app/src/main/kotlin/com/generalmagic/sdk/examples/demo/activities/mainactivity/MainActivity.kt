/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("DEPRECATION")

package com.generalmagic.sdk.examples.demo.activities.mainactivity

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.iterator
import androidx.drawerlayout.widget.DrawerLayout
import com.generalmagic.sdk.core.GemSdk
import com.generalmagic.sdk.core.GemSurfaceView
import com.generalmagic.sdk.examples.demo.R
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.CustomNavController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.CustomPTNavController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.CustomServerUrl
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.CustomSimController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.FlyToArea
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.FlyToCoords
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.FlyToInstr
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.FlyToRoute
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.FlyToTraffic
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.HelloViewController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.LogDataSourceController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.LogPlayerController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.LogRecorderController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.MultipleViewsController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.PredefNavController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.PredefPTNavController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.RouteAb
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.RouteAbc
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.RouteCustom
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.SimLandmarksController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.SimLandmarksController.Companion.EXTRA_WAYPOINTS
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.SimRouteController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.SimRouteController.Companion.EXTRA_ROUTE
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.TwoTiledViewsController
import com.generalmagic.sdk.examples.demo.activities.mainactivity.controllers.WikiController
import com.generalmagic.sdk.examples.demo.app.*
import com.generalmagic.sdk.examples.demo.util.IntentHelper
import com.generalmagic.sdk.places.Coordinates
import com.generalmagic.sdk.places.Landmark
import com.generalmagic.sdk.routesandnavigation.Route
import com.generalmagic.sdk.util.PermissionsHelper
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.Util
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView

class MainActivity : BaseActivity(), IMapControllerActivity {

    lateinit var gem_surface: GemSurfaceView
    lateinit var drawer_layout: DrawerLayout
    lateinit var main_container: LinearLayout
    lateinit var toolbar_content: LinearLayout
    lateinit var appBarLayout: AppBarLayout
    lateinit var toolbar: Toolbar
    lateinit var nav_view: NavigationView
    lateinit var bottomButtons: ConstraintLayout
    lateinit var route_profile: RelativeLayout

    private val appNavMenuListener = NavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.tutorial_hello -> Tutorials.openHelloWorldTutorial()
            R.id.tutorial_styles -> Tutorials.openStylesTutorial()
            R.id.tutorial_twotiledViews -> Tutorials.openTwoTiledViewsTutorial()
            R.id.tutorial_multiple -> Tutorials.openMultipleViewsTutorial()
            R.id.tutorial_settings -> Tutorials.openSettingsTutorial()
            R.id.tutorial_searchtext -> Tutorials.openSearchTextTutorial()
            R.id.tutorial_searchnearby -> Tutorials.openSearchNearbyTutorial()
            R.id.tutorial_searchpoi -> Tutorials.openSearchPoiTutorial()
            R.id.tutorial_addresssearch -> Tutorials.openAddressSearchTutorial()
            R.id.tutorial_history -> Tutorials.openSearchHistoryTutorial()
            R.id.tutorial_favourites -> Tutorials.openSearchFavouritesTutorial()
            R.id.tutorial_route_ab -> Tutorials.openRouteAbTutorial()
            R.id.tutorial_route_abc -> Tutorials.openRouteAbcTutorial()
            R.id.tutorial_route_custom -> Tutorials.openCustomRouteTutorial(arrayListOf())
            R.id.tutorial_predef_sim -> {
                val waypoints = ArrayList<Landmark>()
                SdkCall.execute { Landmark("San Francisco", Coordinates(37.77903, -122.41991)) }
                    ?.let {
                        waypoints.add(
                            it
                        )
                    }
                SdkCall.execute { Landmark("San Jose", Coordinates(37.33619, -121.89058)) }?.let {
                    waypoints.add(
                        it
                    )
                }
                Tutorials.openLandmarksSimulationTutorial(waypoints)
            }
            R.id.tutorial_predef_nav -> Tutorials.openPredefNavigationTutorial()
            R.id.tutorial_predef_ptnav -> Tutorials.openPredefPublicNavTutorial()
            R.id.tutorial_custom_ptnav -> Tutorials.openCustomPublicNavTutorial(arrayListOf())
            R.id.tutorial_custom_sim -> Tutorials.openCustomSimulationTutorial()
            R.id.tutorial_custom_nav -> Tutorials.openCustomNavigationTutorial()
            R.id.tutorial_sensorsList -> Tutorials.openSensorsListTutorial()
            R.id.tutorial_directcam -> Tutorials.openDirectCamTutorial()
            R.id.tutorial_canvasDrawerCam -> Tutorials.openCanvasDrawerCamTutorial()
            R.id.tutorial_logRecorder -> Tutorials.openLogRecorderTutorial()
            R.id.tutorial_logPlayer -> Tutorials.openPickLogTutorial()
            R.id.tutorial_flyto_coords -> Tutorials.openFlyToCoordsTutorial()
            R.id.tutorial_flyto_area -> Tutorials.openFlyToAreaTutorial()
            R.id.tutorial_flyto_instr -> Tutorials.openFlyToNavInstrTutorial()
            R.id.tutorial_flyto_route -> Tutorials.openFlyToRouteTutorial()
            R.id.tutorial_flyto_traffic -> Tutorials.openFlyToTrafficTutorial()
            R.id.tutorial_flyto_line -> Tutorials.openFlyToLineTutorial()
            R.id.tutorial_online_maps -> Tutorials.openOnlineMapsTutorial()
            R.id.tutorial_wiki -> Tutorials.openPredefWikiTutorial()
            R.id.tutorial_custom_url -> Tutorials.openCustomUrlTutorial()

            else -> {
                /* NOTHING */
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        true
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gem_surface = findViewById(R.id.gem_surface)
        drawer_layout = findViewById(R.id.drawer_layout)
        main_container = findViewById(R.id.main_container)
        toolbar_content = findViewById(R.id.toolbar_content)
        appBarLayout = findViewById(R.id.appBarLayout)
        toolbar = findViewById(R.id.toolbar)
        nav_view = findViewById(R.id.nav_view)
        bottomButtons = findViewById(R.id.bottomButtons)
        route_profile = findViewById(R.id.route_profile)


        setSupportActionBar(toolbar)

        // make status bar transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val c: Int = window.statusBarColor
            window.statusBarColor = Color.argb(128, Color.red(c), Color.green(c), Color.blue(c))
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            )
        }

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

        TutorialsOpener.setMapTutorialsOpener(mapTutorialsOpener)

        gem_surface.onSdkInitSucceeded = {
            SdkCall.execute {
                GEMApplication.init(this, gem_surface) {
                    requestPermissions()
                }
            }
        }

        if (!Util.isInternetConnected(this)) {
            Toast.makeText(this, "You must be connected to internet!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()

        SdkCall.execute {
            if (GemSdk.isInitialized())
                GemSdk.notifyBackgroundEvent(GemSdk.EBackgroundEvent.EnterBackground)
        }
    }

    override fun onResume() {
        super.onResume()

        SdkCall.execute {
            if (GemSdk.isInitialized())
                GemSdk.notifyBackgroundEvent(GemSdk.EBackgroundEvent.LeaveBackground)
        }
    }

    override fun doBackPressed(): Boolean {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
            return true
        }

        return false
    }

    override fun displayCloseAppDialog() {
        val dialogView: View = View.inflate(this, R.layout.bottom_sheet_dialog, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        dialogView.findViewById<ImageView>(R.id.icon)
            .setImageResource(R.drawable.ic_warning_blue_24dp)

        dialogView.findViewById<TextView>(R.id.message).text =
            getText(R.string.exit_application)

        val positiveButton = dialogView.findViewById<Button>(R.id.positiveButton)
        positiveButton.text = getText(R.string.yes)
        positiveButton.setOnClickListener {
            super.onBackPressed()
            dialog.dismiss()
            GEMApplication.onCloseAppDialogResponse(true)
        }

        val negativeButton = dialogView.findViewById<Button>(R.id.negativeButton)
        negativeButton.text = getText(R.string.no)
        negativeButton.setOnClickListener {
            dialog.dismiss()
            GEMApplication.onCloseAppDialogResponse(false)
        }

        dialog.setOnShowListener {
            dialog.behavior.setPeekHeight(dialogView.height)
        }

        dialog.show()
    }

    override fun onRequestPermissionsFinish(granted: Boolean) {
        if (!granted) {
            GEMApplication.terminateApp()
        }
    }

    private fun requestPermissions(): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
//            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION) //DO NOT UNCOMMENT!
        }

        return PermissionsHelper.requestPermissions(
            GEMApplication.REQUEST_PERMISSIONS,
            this,
            permissions.toTypedArray()
        )
    }

    override fun setAppBarVisible(visible: Boolean) {
        if (visible) {
            supportActionBar?.show()
            drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } else {
            supportActionBar?.hide()
            drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
    }

    override fun getNavigationView(): NavigationView {
        return nav_view
    }

    /** ////////////////////////////////////////////////////////////////////////////// */
    ///             IMapControllerActivity
    /** ////////////////////////////////////////////////////////////////////////////// */

    override fun getBottomLeftButton(): FloatingActionButton? =
        bottomButtons.findViewById(R.id.bottomLeftButton)

    override fun getBottomCenterButton(): FloatingActionButton? =
        bottomButtons.findViewById(R.id.bottomCenterButton)

    override fun getBottomRightButton(): FloatingActionButton? =
        bottomButtons.findViewById(R.id.bottomRightButton)

    override fun getRouteProfileView(): View = route_profile

    override fun setFixedOrientation(orientation: Int) {
        requestedOrientation = orientation
    }

    /** ////////////////////////////////////////////////////////////////////////////// */
    ///                             MAP TUTORIALS
    /** ////////////////////////////////////////////////////////////////////////////// */

    private val mapTutorialsOpener = object : TutorialsOpener.ITutorialsOpener {
        override fun preOpenTutorial() {
            main_container.removeAllViews()
            toolbar_content.removeAllViews()
            appBarLayout.visibility = View.VISIBLE

            bottomButtons.let { buttons ->
                for (button in buttons)
                    button.visibility = View.GONE
            }
        }

        override fun onTutorialOpened(value: TutorialsOpener.ITutorialController) {}

        /////

        override fun openTutorial(id: Tutorials.Id, args: Array<out Any>): Boolean {
            val contentMain = main_container

            val controller: MapLayoutController? = when (id) {
                Tutorials.Id.HelloWorld -> {
                    nav_view.setCheckedItem(R.id.tutorial_hello)
                    layoutInflater.inflate(
                        R.layout.tutorial_hello, contentMain
                    ).findViewById<HelloViewController>(R.id.helloViewController)
                }

                Tutorials.Id.Wiki -> {
                    if (args.isNotEmpty()) {
                        val landmark = args[0] as Landmark?
                        landmark ?: return false
                        IntentHelper.addObjectForKey(landmark, WikiController.EXTRA_LANDMARK)
                    }

                    layoutInflater.inflate(
                        R.layout.tutorial_wiki,
                        contentMain
                    ).findViewById<WikiController>(R.id.wikiController)
                }

                Tutorials.Id.LogPlayer_PLAY -> {
                    val filepath = args[0] as String

                    IntentHelper.addObjectForKey(filepath, LogDataSourceController.EXTRA_FILEPATH)

                    layoutInflater.inflate(
                        R.layout.tutorial_logplayer,
                        contentMain
                    ).findViewById<LogPlayerController>(R.id.logPlayerController)
                }

                Tutorials.Id.TwoTiledViews -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_twotiledviews,
                        contentMain
                    ).findViewById<TwoTiledViewsController>(R.id.twoTiledViewsController)
                }

                Tutorials.Id.MultipleViews -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_multiplemaps,
                        contentMain
                    ).findViewById<MultipleViewsController>(R.id.multipleViewsController)
                }

                Tutorials.Id.Route_AB -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_route_ab,
                        contentMain
                    ).findViewById<RouteAb>(R.id.routeAbController)
                }

                Tutorials.Id.Route_ABC -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_route_abc,
                        contentMain
                    ).findViewById<RouteAbc>(R.id.routeAbcController)
                }

                Tutorials.Id.Route_Custom -> {
                    val waypoints = ArrayList<Landmark>()
                    if (args.isNotEmpty()) {
                        val genericArrayList = args[0] as ArrayList<*>
                        for (item in genericArrayList) {
                            val castedItem = item as Landmark
                            waypoints.add(castedItem)
                        }
                    }

                    IntentHelper.addObjectForKey(waypoints, EXTRA_WAYPOINTS)

                    layoutInflater.inflate(
                        R.layout.tutorial_route_custom,
                        contentMain
                    ).findViewById<RouteCustom>(R.id.routeCustomController)
                }

                Tutorials.Id.Simulation_Landmarks -> {
                    val waypoints = ArrayList<Landmark>()
                    if (args.isNotEmpty()) {
                        val genericArrayList = args[0] as ArrayList<*>
                        for (item in genericArrayList) {
                            val castedItem = item as Landmark
                            waypoints.add(castedItem)
                        }
                    }

                    IntentHelper.addObjectForKey(waypoints, EXTRA_WAYPOINTS)

                    layoutInflater.inflate(
                        R.layout.tutorial_sim_landmarks,
                        contentMain
                    ).findViewById<SimLandmarksController>(R.id.simLandmarksController)
                }

                Tutorials.Id.Simulation_Route -> {
                    var route: Route? = null
                    if (args.isNotEmpty()) {
                        route = args[0] as Route?
                    }

                    IntentHelper.addObjectForKey(route, EXTRA_ROUTE)

                    layoutInflater.inflate(
                        R.layout.tutorial_sim_route,
                        contentMain
                    ).findViewById<SimRouteController>(R.id.simRouteController)
                }

                Tutorials.Id.Navigation_Predef -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_predef_nav,
                        contentMain
                    ).findViewById<PredefNavController>(R.id.predefNavController)
                }

                Tutorials.Id.PublicNav_Predef -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_predef_ptnav,
                        contentMain
                    ).findViewById<PredefPTNavController>(R.id.predefPtNavController)
                }

                Tutorials.Id.PublicNav_Custom -> {
                    val waypoints = ArrayList<Landmark>()
                    if (args.isNotEmpty()) {
                        val genericArrayList = args[0] as ArrayList<*>
                        for (item in genericArrayList) {
                            val castedItem = item as Landmark
                            waypoints.add(castedItem)
                        }
                    }

                    IntentHelper.addObjectForKey(waypoints, EXTRA_WAYPOINTS)

                    layoutInflater.inflate(
                        R.layout.tutorial_custom_ptnav,
                        contentMain
                    ).findViewById<CustomPTNavController>(R.id.customPtNavController)
                }

                Tutorials.Id.Simulation_Custom -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_sim,
                        contentMain
                    ).findViewById<CustomSimController>(R.id.customSimController)
                }

                Tutorials.Id.Navigation_Custom -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_nav,
                        contentMain
                    ).findViewById<CustomNavController>(R.id.customNavController)
                }

                Tutorials.Id.CanvasDrawerCam -> {
//                layoutInflater.inflate(
//                    R.layout.tutorial_drawercam,
//                    contentMain
//                ).canvasDrawerCamController
                    null
                }

                Tutorials.Id.LogRecorder -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_logrecorder,
                        contentMain
                    ).findViewById<LogRecorderController>(R.id.logRecorderController)
                }

                Tutorials.Id.FlyTo_Coords -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_coords,
                        contentMain
                    ).findViewById<FlyToCoords>(R.id.flyToCoordsController)
                }

                Tutorials.Id.FlyTo_Area -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_area,
                        contentMain
                    ).findViewById<FlyToArea>(R.id.flyToAreaController)
                }

                Tutorials.Id.FlyTo_Instr -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_instr,
                        contentMain
                    ).findViewById<FlyToInstr>(R.id.flyToInstrController)
                }

                Tutorials.Id.FlyTo_Route -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_route,
                        contentMain
                    ).findViewById<FlyToRoute>(R.id.flyToRouteController)
                }

                Tutorials.Id.FlyTo_Traffic -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_traffic,
                        contentMain
                    ).findViewById<FlyToTraffic>(R.id.flyToTrafficController)
                }

                Tutorials.Id.FlyTo_Line -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_line,
                        contentMain
                    ).findViewById<FlyToTraffic>(R.id.flyToLineController)
                }

                Tutorials.Id.CustomUrl -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_url,
                        contentMain
                    ).findViewById<CustomServerUrl>(R.id.customServerController)
                }

                else -> null
            }

            controller ?: return false

            controller.mapActivity = this@MainActivity

            return true
        }

    }
}
