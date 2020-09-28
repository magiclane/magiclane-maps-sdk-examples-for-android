/*
 * Copyright (C) 2019-2020, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.activities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.generalmagic.gemsdk.demo.R
import com.generalmagic.gemsdk.demo.util.GEMApplication
import com.generalmagic.gemsdk.demo.util.KeyboardUtil
import com.generalmagic.gemsdk.demo.util.Utils
import com.generalmagic.gemsdk.util.GEMError
import kotlinx.android.synthetic.main.activity_list_view.*
import kotlin.system.exitProcess

open class BaseActivity : AppCompatActivity() {
    private var mLastDisplayedError: GEMError = GEMError.KNoError
    override fun onResume() {
        super.onResume()

        GEMApplication.topActivity = this
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

    // / PERMISSIONS

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
