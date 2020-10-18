/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.activities.WebActivity
import com.generalmagic.gemsdk.demo.activities.pickvideo.PickLogActivity
import com.generalmagic.gemsdk.demo.activities.searchaddress.SearchAddressActivity
import com.generalmagic.gemsdk.demo.app.StaticsHolder.Companion.getMainMapView
import com.generalmagic.gemsdk.demo.controllers.*
import com.generalmagic.gemsdk.demo.util.KeyboardUtil
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.models.ContentStoreItem
import com.generalmagic.gemsdk.util.GEMError
import com.generalmagic.gemsdk.util.GEMSdkCall
import kotlinx.android.synthetic.main.activity_list_view.progressBar
import kotlinx.android.synthetic.main.app_bar_layout.*
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
import kotlinx.android.synthetic.main.tutorial_predef_sim.view.*
import kotlinx.android.synthetic.main.tutorial_route_ab.view.*
import kotlinx.android.synthetic.main.tutorial_route_abc.view.*
import kotlinx.android.synthetic.main.tutorial_route_custom.view.*
import kotlinx.android.synthetic.main.tutorial_twotiledviews.view.*
import kotlinx.android.synthetic.main.tutorial_wiki.view.*
import kotlin.system.exitProcess

open class BaseActivity : AppCompatActivity() {
    private var mLastDisplayedError: GEMError = GEMError.KNoError

    override fun onResume() {
        super.onResume()
        GEMApplication.activityStack.push(this)
    }

    override fun onPause() {
        super.onPause()
        GEMApplication.activityStack.pop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onNavigateUp(): Boolean {
        KeyboardUtil.hideKeyboard(this)
        onBackPressed()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        KeyboardUtil.hideKeyboard(this)
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_show_code -> {
                val intent = Intent(this, WebActivity::class.java)
                intent.putExtra("url", "https://www.generalmagic.com")
                startActivity(intent)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    open fun refresh() {}

    fun showErrorMessage(error: GEMError) {
        if (mLastDisplayedError == error) {
            return
        }

        mLastDisplayedError = error
        showErrorMessage(Utils.getErrorMessage(error))
    }

    open fun showErrorMessage(error: String) {
        Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
    }

    fun terminateApp() {
        finish()
        exitProcess(0)
    }

    fun disableScreenLock() {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun enableScreenLock() {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun hideBusyIndicator() {
        hideProgress()
    }

    fun showBusyIndicator() {
        showProgress()
    }

    fun showProgress() {
        progressBar?.visibility = View.VISIBLE
    }

    fun hideProgress() {
        progressBar?.visibility = View.GONE
    }

    @Suppress("deprecation")
    fun showSystemBars() {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    @Suppress("deprecation")
    fun hideSystemBars() {
        window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    // ----------------------------------------------------------------------------------------------
    protected var defaultMapStyle: ContentStoreItem? = null

    protected var lastSelectedLayoutId: Int = R.id.tutorial_hello
    protected var currentController: BaseLayoutController? = null
    protected fun selectLayout(layoutId: Int) {
        val contentMain = main_container as ViewGroup
        contentMain.removeAllViews()
        toolbar_content.removeAllViews()
        appBarLayout.visibility = android.view.View.VISIBLE

        currentController = null

        KeyboardUtil.hideKeyboard(this)

        GEMSdkCall.execute {
            val prefs = getMainMapView()?.preferences() ?: return@execute

            prefs.routes()?.clear()
            defaultMapStyle?.let { prefs.setMapStyleById(it.getId()) }
        }

        GEMSdkCall.execute { MapFollowingStatusProvider.getInstance().doFollowStop() }
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
//                currentController = layoutInflater.inflate(
//                    R.layout.tutorial_drawercam,
//                    contentMain
//                ).canvasDrawerCamController
            }

            R.id.tutorial_logRecorder -> {
                currentController = layoutInflater.inflate(
                    R.layout.tutorial_logrecorder,
                    contentMain
                ).logRecorderController
            }

            R.id.tutorial_logPlayer -> {
                val videosDir = GEMApplication.appContext?.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                videosDir ?: return

                val intent = Intent(this, PickLogActivity::class.java)
                intent.putExtra(PickLogActivity.INPUT_DIR, videosDir.absolutePath)
                startActivityForResult(intent, CODE_RESULT_SELECT_VIDEO)
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

    // ----------------------------------------------------------------------------------------------

    private val CODE_RESULT_SELECT_VIDEO = 100
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            CODE_RESULT_SELECT_VIDEO -> {
                if (data == null) {
                    Toast.makeText(this, "Please pick again!", Toast.LENGTH_SHORT).show()
                    return
                }

                val contentMain = main_container as ViewGroup
                val logPlayerController = layoutInflater.inflate(
                    R.layout.tutorial_logplayer,
                    contentMain
                ).logPlayerController

                val pickedPath = data.getStringExtra(PickLogActivity.RESULT_VIDEO_PATH) ?: return
                logPlayerController.videoPath = pickedPath

                currentController = logPlayerController
                GEMSdkCall.execute { currentController?.doStart() }
            }
            else -> {
                // uknown answer
            }
        }
    }

    // ----------------------------------------------------------------------------------------------
    // / PERMISSIONS
    // ----------------------------------------------------------------------------------------------

    private val REQUEST_PERMISSIONS = 110

    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    open fun onRequestPermissionsFinish(granted: Boolean) {}

    fun requestPermissions(permissions: Array<String>): Boolean {
        var requested = false
        if (!hasPermissions(this, permissions)) {
            requested = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions, REQUEST_PERMISSIONS)
            }
// 			else {
// 				//TODO: implement this
// 			}
        }

        return requested
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) {
            return
        }

        val result = grantResults[0]
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                onRequestPermissionsFinish(result == PackageManager.PERMISSION_GRANTED)
            }
        }
    }
}
