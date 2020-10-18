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

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import java.util.*

class GEMApplication {
    companion object {
        val activityStack = Stack<Activity>()
        var uiHandler = Handler(Looper.getMainLooper())
        var appContext: Context? = null

        /** absolute path temporary internal records path*/
        var iRecordsPath = ""

        /** absolute path where records will be externally saved. */
        var eRecordsPath = ""

        fun topActivity(): Activity? {
            try {
                activityStack.peek()
            } catch (e: Exception) {}

            return null
        }

        fun getApplicationContext(): Context {
            return appContext!!
        }

        fun getAppResources(): Resources {
            return getApplicationContext().resources
        }

//        fun runOnUiThread(action: Runnable?){
//            topActivity()?.runOnUiThread(action)
//        }

        fun postOnMain(action: Runnable) {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}
