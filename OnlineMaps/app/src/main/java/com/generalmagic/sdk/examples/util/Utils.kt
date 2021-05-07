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

object Utils {
    ////////////////////////////////////////////////////////////////////////////////////////////////

    fun formatSizeAsText(inSizeInBytes: Long): String {
        var sizeInBytes: Double = inSizeInBytes.toDouble()
        val unit: String

        when {
            sizeInBytes < (1024.0 * 1024.0) -> {
                sizeInBytes /= 1024.0
                unit = "KB"
            }
            sizeInBytes < (1024.0 * 1024.0 * 1024.0) -> {
                sizeInBytes /= (1024.0 * 1024.0)
                unit = "MB"
            }
            else -> {
                sizeInBytes /= (1024.0 * 1024.0 * 1024.0)
                unit = "GB"
            }
        }

        var size = String.format("%.2f", sizeInBytes)

        val pos = size.indexOf(".00")
        if ((pos > 0) && (pos == (size.length - 3))) {
            size = size.substring(0, pos)
        }

        return String.format("%s %s", size, unit)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
}
