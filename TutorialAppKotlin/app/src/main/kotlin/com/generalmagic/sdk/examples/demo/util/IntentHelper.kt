/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.demo.util

import java.util.*

object IntentHelper {
    private val mMappedData: Hashtable<String, Any> = Hashtable<String, Any>()

    fun addObjectForKey(obj: Any?, key: String?) {
        mMappedData[key] = obj
    }

    fun getObjectForKey(key: String?): Any? {
        val data: Any? = mMappedData[key]
        mMappedData.remove(key)
        return data
    }
}
