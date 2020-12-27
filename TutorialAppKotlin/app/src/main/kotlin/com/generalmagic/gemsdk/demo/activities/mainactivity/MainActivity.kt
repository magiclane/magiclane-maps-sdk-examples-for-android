/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities.mainactivity

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.iterator
import androidx.drawerlayout.widget.DrawerLayout
import com.generalmagic.gemsdk.AppVariant
import com.generalmagic.gemsdk.Route
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.LogDataSourceController
import com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.SimLandmarksController.Companion.EXTRA_WAYPOINTS
import com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.SimRouteController.Companion.EXTRA_ROUTE
import com.generalmagic.gemsdk.demo.activities.mainactivity.controllers.WikiController
import com.generalmagic.gemsdk.demo.app.*
import com.generalmagic.gemsdk.demo.util.IntentHelper
import com.generalmagic.gemsdk.mapview.GEMMapSurface
import com.generalmagic.gemsdk.models.Coordinates
import com.generalmagic.gemsdk.models.Landmark
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_layout.*
import kotlinx.android.synthetic.main.app_bar_layout.view.*
import kotlinx.android.synthetic.main.tutorial_custom_nav.view.*
import kotlinx.android.synthetic.main.tutorial_custom_ptnav.view.*
import kotlinx.android.synthetic.main.tutorial_custom_sim.view.*
import kotlinx.android.synthetic.main.tutorial_custom_url.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_area.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_coords.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_instr.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_route.view.*
import kotlinx.android.synthetic.main.tutorial_flyto_traffic.view.*
import kotlinx.android.synthetic.main.tutorial_hello.view.*
import kotlinx.android.synthetic.main.tutorial_logplayer.view.*
import kotlinx.android.synthetic.main.tutorial_logrecorder.view.*
import kotlinx.android.synthetic.main.tutorial_multiplemaps.view.*
import kotlinx.android.synthetic.main.tutorial_predef_nav.view.*
import kotlinx.android.synthetic.main.tutorial_predef_ptnav.view.*
import kotlinx.android.synthetic.main.tutorial_route_ab.view.*
import kotlinx.android.synthetic.main.tutorial_route_abc.view.*
import kotlinx.android.synthetic.main.tutorial_route_custom.view.*
import kotlinx.android.synthetic.main.tutorial_sim_landmarks.view.*
import kotlinx.android.synthetic.main.tutorial_sim_route.view.*
import kotlinx.android.synthetic.main.tutorial_twotiledviews.view.*
import kotlinx.android.synthetic.main.tutorial_wiki.view.*


class MainActivity : BaseActivity(), IMapControllerActivity {
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
            R.id.tutorial_route_ab -> Tutorials.openRouteAbTutorial()
            R.id.tutorial_route_abc -> Tutorials.openRouteAbcTutorial()
            R.id.tutorial_route_custom -> Tutorials.openCustomRouteTutorial()
            R.id.tutorial_predef_sim -> {
                val waypoints = ArrayList<Landmark>()
                waypoints.add(Landmark("San Francisco", Coordinates(37.77903, -122.41991)))
                waypoints.add(Landmark("San Jose", Coordinates(37.33619, -121.89058)))
                Tutorials.openLandmarksSimulationTutorial(waypoints)
            }
            R.id.tutorial_predef_nav -> Tutorials.openPredefNavigationTutorial()
            R.id.tutorial_predef_ptnav -> Tutorials.openPredefPublicNavTutorial()
            R.id.tutorial_custom_ptnav -> Tutorials.openCustomPublicNavTutorial()
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
            R.id.tutorial_online_maps -> Tutorials.openOnlineMapsTutorial()
            R.id.tutorial_wiki -> Tutorials.openPredefWikiTutorial()
            R.id.tutorial_custom_url -> Tutorials.openCustomUrlTutorial()

            else -> {
                /* NOTHING */
            }
        }

        drawer_layout?.closeDrawer(GravityCompat.START)
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GEMMapSurface.appVariant = AppVariant.VARIANT_MAGICEARTH_BETA

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // make status bar transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            val c: Int = window.statusBarColor
            window.statusBarColor = Color.argb(128, Color.red(c), Color.green(c), Color.blue(c))
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        else
        {
            window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
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

        mapTutorialsOpener.init(this)
        TutorialsOpener.setMapTutorialsOpener(mapTutorialsOpener)
        GEMApplication.init(this, gem_surface) {
            requestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        gem_surface.onPause()
    }

    override fun onResume() {
        super.onResume()
        gem_surface.onResume()
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

    private var firstWave = true
    override fun onRequestPermissionsFinish(granted: Boolean) {
        if (!granted) {
            GEMApplication.terminateApp()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (firstWave) {
                GEMApplication.requestPermissions(
                    this, arrayListOf(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ).toTypedArray()
                )
                firstWave = false
            }
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

        return GEMApplication.requestPermissions(this, permissions.toTypedArray())
    }

    override fun setAppBarVisible(visible: Boolean) {
        if (visible) {
            supportActionBar?.show()
            drawer_layout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        } else {
            supportActionBar?.hide()
            drawer_layout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
    }

    override fun getNavigationView(): NavigationView? {
        return nav_view
    }

    /** ////////////////////////////////////////////////////////////////////////////// */
    ///             IMapControllerActivity
    /** ////////////////////////////////////////////////////////////////////////////// */

    override fun getBottomLeftButton(): FloatingActionButton? = bottomButtons.bottomLeftButton
    override fun getBottomCenterButton(): FloatingActionButton? = bottomButtons.bottomCenterButton
    override fun getBottomRightButton(): FloatingActionButton? = bottomButtons.bottomRightButton

    override fun setFixedOrientation(orientation: Int) {
        requestedOrientation = orientation
    }

    /** ////////////////////////////////////////////////////////////////////////////// */
    ///                             MAP TUTORIALS
    /** ////////////////////////////////////////////////////////////////////////////// */

    private val mapTutorialsOpener = object : TutorialsOpener.ITutorialsOpener {
        lateinit var contentMain: LinearLayout
        lateinit var context: Context
        /////

        fun init(mainActivity: MainActivity) {
            contentMain = mainActivity.main_container
            context = mainActivity
        }

        override fun preOpenTutorial() {
            contentMain.removeAllViews()
            toolbar_content.removeAllViews()
            appBarLayout.visibility = View.VISIBLE

            bottomButtons?.let { buttons ->
                for (button in buttons)
                    button.visibility = View.GONE
            }
        }

        override fun onTutorialOpened(value: TutorialsOpener.ITutorialController) {}

        /////

        override fun openTutorial(id: Tutorials.Id, args: Array<out Any>): Boolean {
            val controller: MapLayoutController? = when (id) {
                Tutorials.Id.HelloWorld -> {
                    nav_view.setCheckedItem(R.id.tutorial_hello)
                    layoutInflater.inflate(
                        R.layout.tutorial_hello, contentMain
                    ).helloViewController
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
                    ).wikiController
                }

                Tutorials.Id.LogPlayer_PLAY -> {
                    val filepath: String? = args[0] as String
                    filepath ?: return false

                    IntentHelper.addObjectForKey(filepath, LogDataSourceController.EXTRA_FILEPATH)

                    layoutInflater.inflate(
                        R.layout.tutorial_logplayer,
                        contentMain
                    ).logPlayerController
                }

                Tutorials.Id.TwoTiledViews -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_twotiledviews,
                        contentMain
                    ).twoTiledViewsController
                }

                Tutorials.Id.MultipleViews -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_multiplemaps,
                        contentMain
                    ).multipleViewsController
                }

                Tutorials.Id.Route_AB -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_route_ab,
                        contentMain
                    ).routeAbController
                }

                Tutorials.Id.Route_ABC -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_route_abc,
                        contentMain
                    ).routeAbcController
                }

                Tutorials.Id.Route_Custom -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_route_custom,
                        contentMain
                    ).routeCustomController
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
                    ).simLandmarksController
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
                    ).simRouteController
                }

                Tutorials.Id.Navigation_Predef -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_predef_nav,
                        contentMain
                    ).predefNavController
                }

                Tutorials.Id.PublicNav_Predef -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_predef_ptnav,
                        contentMain
                    ).predefPtNavController
                }

                Tutorials.Id.PublicNav_Custom -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_ptnav,
                        contentMain
                    ).customPtNavController
                }

                Tutorials.Id.Simulation_Custom -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_sim,
                        contentMain
                    ).customSimController
                }

                Tutorials.Id.Navigation_Custom -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_nav,
                        contentMain
                    ).customNavController
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
                    ).logRecorderController
                }

                Tutorials.Id.FlyTo_Coords -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_coords,
                        contentMain
                    ).flyToCoordsController
                }

                Tutorials.Id.FlyTo_Area -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_area,
                        contentMain
                    ).flyToAreaController
                }

                Tutorials.Id.FlyTo_Instr -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_instr,
                        contentMain
                    ).flyToInstrController
                }

                Tutorials.Id.FlyTo_Route -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_route,
                        contentMain
                    ).flyToRouteController
                }

                Tutorials.Id.FlyTo_Traffic -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_flyto_traffic,
                        contentMain
                    ).flyToTrafficController
                }

                Tutorials.Id.CustomUrl -> {
                    layoutInflater.inflate(
                        R.layout.tutorial_custom_url,
                        contentMain
                    ).customServerController
                }

                else -> null
            }

            controller ?: return false

            controller.mapActivity = this@MainActivity

            return true
        }

    }
}
