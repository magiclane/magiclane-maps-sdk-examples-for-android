/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.example.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import kotlin.system.exitProcess

class Util {
    companion object {
        fun terminateApp(activity: Activity) {
            activity.finish()
            exitProcess(0)
        }

        fun postOnMain(task: Runnable) {
            Handler(Looper.getMainLooper()).post(task)
        }
    }
}
