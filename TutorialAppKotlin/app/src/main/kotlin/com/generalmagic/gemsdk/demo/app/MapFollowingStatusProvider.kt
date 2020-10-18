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

import com.generalmagic.gemsdk.Animation
import com.generalmagic.gemsdk.TAnimation
import com.generalmagic.gemsdk.TFlyModes
import com.generalmagic.gemsdk.util.GEMSdkCall
import java.util.concurrent.atomic.AtomicBoolean

class MapFollowingStatusProvider {
    companion object {
        private var instance: MapFollowingStatusProvider? = null
        fun getInstance(): MapFollowingStatusProvider {
            if (instance == null) instance = MapFollowingStatusProvider()
            return instance!!
        }
    }

    abstract class Listener {
        abstract fun statusChangedTo(following: Boolean)
    }

    fun noticePanInterruptFollow() {
        for (listener in listeners) {
            listener.statusChangedTo(false)
        }
    }

    private var isFollowing = AtomicBoolean()
    fun isFollowing() = isFollowing

    fun doFollowStop() {
        GEMSdkCall.checkCurrentThread()
        val mainMapView = StaticsHolder.getMainMapView() ?: return
        mainMapView.stopFollowingPosition()

        isFollowing.set(false)
        for (listener in listeners) {
            listener.statusChangedTo(isFollowing.get())
        }
    }

    fun doFollowStart() {
        GEMSdkCall.checkCurrentThread()
        val mainMapView = StaticsHolder.getMainMapView() ?: return

        val animation = Animation()
        animation.setMethod(TAnimation.EAnimationFly)
        animation.setFly(TFlyModes.EFM_Linear)
        animation.setDuration(900)

        mainMapView.startFollowingPosition(animation)

        isFollowing.set(true)
        for (listener in listeners) {
            listener.statusChangedTo(isFollowing.get())
        }
    }

    val listeners = ArrayList<Listener>()
}
