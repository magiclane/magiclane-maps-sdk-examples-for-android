/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.gemsdk.demo.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.generalmagic.gemsdk.demo.app.elements.ButtonsDecorator
import com.google.android.material.floatingactionbutton.FloatingActionButton

interface IMapControllerActivity {
    fun getBottomLeftButton(): FloatingActionButton?
    fun getBottomCenterButton(): FloatingActionButton?
    fun getBottomRightButton(): FloatingActionButton?
    fun setFixedOrientation(orientation: Int)
    fun setProgressVisible(visible: Boolean)
    fun setSystemBarsVisible(visible: Boolean)
    fun setScreenAlwaysOn(enabled: Boolean)
}

open class MapLayoutController(context: Context, attrs: AttributeSet?) :
    ConstraintLayout(context, attrs), TutorialsOpener.ITutorialController {

    lateinit var mapActivity: IMapControllerActivity

    final override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }

    final override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        GEMApplication.postOnMain { // do not remove
            onCreated()
        }
    }

    final override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        GEMApplication.postOnMain { // do not remove
            onDestroyed()
        }
    }

    open fun onCreated() {
        TutorialsOpener.onTutorialCreated(this)
        onMapFollowStatusChanged(GEMApplication.isFollowingGps())
    }

    open fun onDestroyed() {
        TutorialsOpener.onTutorialDestroyed(this)
    }

    override fun doStart() {}
    override fun doStop() {}
    override fun doBackPressed(): Boolean {
        doStop()
        Tutorials.openHelloWorldTutorial()
        return true
    }

    override fun onMapFollowStatusChanged(following: Boolean) {
        hideAllButtons()

        if (!following) {
            setFollowGpsButtonVisible(true)
        }
    }

    fun hideProgress() = mapActivity.setProgressVisible(false)
    fun showProgress() = mapActivity.setProgressVisible(true)

    fun setSystemBarsVisible(visible: Boolean) = mapActivity.setSystemBarsVisible(visible)
    fun setScreenAlwaysOn(enabled: Boolean) = mapActivity.setScreenAlwaysOn(enabled)
    fun setFixedOrientation(orientation: Int) = mapActivity.setFixedOrientation(orientation)

    // ----------------------------------------------------------------------------------------------
    //                              BUTTONS
    // ----------------------------------------------------------------------------------------------

    fun getBottomLeftButton(): FloatingActionButton? = mapActivity.getBottomLeftButton()
    fun getBottomCenterButton(): FloatingActionButton? = mapActivity.getBottomCenterButton()
    fun getBottomRightButton(): FloatingActionButton? = mapActivity.getBottomRightButton()

    fun hideAllButtons() {
        getBottomLeftButton()?.visibility = View.GONE
        getBottomCenterButton()?.visibility = View.GONE
        getBottomRightButton()?.visibility = View.GONE
    }

    fun setStopButtonVisible(visible: Boolean) {
        val button = getBottomLeftButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsStop(context, button) {
                doStop()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    fun setStartButtonVisible(visible: Boolean) {
        val button = getBottomLeftButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsStart(context, button) {
                doStart()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    fun setFollowGpsButtonVisible(visible: Boolean) {
        val button = getBottomLeftButton() ?: return

        if (visible) {
            ButtonsDecorator.buttonAsFollowGps(context, button) {
                GEMApplication.onFollowGpsPressed()
            }

            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }
}
