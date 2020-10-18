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

import androidx.drawerlayout.widget.DrawerLayout
import com.generalmagic.gemsdk.*
import com.generalmagic.gemsdk.demo.MainActivity
import com.generalmagic.gemsdk.models.Screen

class StaticsHolder {
    companion object {
        var getMainMapView: () -> View? = { null }
        var gemMapScreen: () -> Screen? = { null }
        var getMainActivity: () -> MainActivity? = { null }
        var getNavViewDrawerLayout: () -> DrawerLayout? = { null }
        var getGlContext: () -> OpenGLContext? = { null }
    }
}
