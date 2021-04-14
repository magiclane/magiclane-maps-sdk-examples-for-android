/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.generalmagic.sdk.examples.util.SdkInitHelper.terminateApp
import com.generalmagic.sdk.util.SdkCall

class PermissionsHelper {
    companion object {
        fun requestPermissions(activity: Activity): Boolean {
            val permissions = arrayListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE)
            }

            return requestPermissions(activity, permissions.toTypedArray())
        }

        private val REQUEST_PERMISSIONS = 110
        fun requestPermissions(activity: Activity, permissions: Array<String>): Boolean {
            var requested = false
            if (!hasPermissions(activity, permissions)) {
                requested = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(permissions, REQUEST_PERMISSIONS)
                }
            } else {
                return true
            }
//            }

            return requested
        }

        fun onRequestPermissionsResult(
            activity: Activity, requestCode: Int, permissions: Array<String>, grantResults: IntArray
        ) {
            if (grantResults.isEmpty())
                return

            val result = grantResults[0]
            when (requestCode) {
                REQUEST_PERMISSIONS -> {
                    SdkCall.execute {
                        com.generalmagic.sdk.util.PermissionsHelper.produce()
                            ?.notifyOnPermissionsStatusChanged()
                    }
                    onRequestPermissionsFinish(
                        activity,
                        result == PackageManager.PERMISSION_GRANTED
                    )
                }
            }
        }

        fun hasPermissions(context: Context, permission: String): Boolean =
            hasPermissions(context, arrayOf(permission))

        fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
            permissions.all {
                ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

        private var firstWave = true
        fun onRequestPermissionsFinish(activity: Activity, granted: Boolean) {
            if (!granted) {
                terminateApp(activity)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (firstWave) {
                    requestPermissions(
                        activity, arrayListOf(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ).toTypedArray()
                    )
                    firstWave = false
                }
            }
        }
    }
}
