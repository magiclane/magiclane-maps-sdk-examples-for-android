/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.demo.util

import java.util.*

class IntentHelper private constructor() {
    private val _hash: Hashtable<String, Any> = Hashtable<String, Any>()

    companion object {
        private var instance: IntentHelper? = null
        private fun getInstance(): IntentHelper {
            if (instance == null) {
                instance = IntentHelper()
            }
            return instance!!
        }

        fun addObjectForKey(obj: Any?, key: String?) {
            getInstance()._hash[key] = obj
        }

        fun getObjectForKey(key: String?): Any? {
            val helper: IntentHelper? = getInstance()
            val data: Any? = helper?._hash?.get(key)
            helper?._hash?.remove(key)
            return data
        }
    }
}
