/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.navigation.NavigationView

@Suppress("DEPRECATION")
open class BaseActivity : AppCompatActivity(), TutorialsOpener.ITutorialController {

    lateinit var progressBar: ProgressBar

    // SYSTEM METHODS
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GEMApplication.onActivityCreated(this, savedInstanceState)
        TutorialsOpener.onTutorialCreated(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        TutorialsOpener.onTutorialDestroyed(this)
        GEMApplication.onDestroyed(this)
    }

    override fun onResume() {
        super.onResume()
        GEMApplication.onActivityResumed(this)
    }

    override fun onPause() {
        super.onPause()
        GEMApplication.onActivityPaused(this)
    }

    final override fun onBackPressed() {
        GEMApplication.onBackPressed(this)
    }

    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        return GEMApplication.onCreateOptionsMenu(this, menu)
    }

    final override fun onNavigateUp(): Boolean = GEMApplication.onNavigateUp(this)
    final override fun onSupportNavigateUp(): Boolean = GEMApplication.onSupportNavigateUp(this)
    final override fun onOptionsItemSelected(item: MenuItem): Boolean {
        GEMApplication.onOptionsItemSelected(this, item)
        return super.onOptionsItemSelected(item)
    }

    final override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        GEMApplication.onActivityResult(this, requestCode, resultCode, data)
    }

    final override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) = GEMApplication.onRequestPermissionsResult(this, requestCode, permissions, grantResults)

    // APP METHODS

    fun setScreenAlwaysOn(enabled: Boolean) {
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Suppress("deprecation")
    fun setSystemBarsVisible(visible: Boolean) {
        if (visible) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    fun showProgress() = setProgressVisible(true)
    fun hideProgress() = setProgressVisible(false)

    fun setProgressVisible(visible: Boolean) {
        if (this::progressBar.isInitialized)
            if (visible)
                progressBar.visibility = View.VISIBLE
            else
                progressBar.visibility = View.GONE
    }

    // ITutorialController

    override fun doBackPressed(): Boolean {
        finish()

        Tutorials.openHelloWorldTutorial()
        return true
    }

    override fun doStart() {}
    override fun doStop() {}
    override fun onMapFollowStatusChanged(following: Boolean) {}

    open fun onRequestPermissionsFinish(granted: Boolean) {}

    open fun refresh() {}

    open fun setAppBarVisible(visible: Boolean) {}

    open fun getNavigationView(): NavigationView? = null

    open fun displayCloseAppDialog() {}
}
