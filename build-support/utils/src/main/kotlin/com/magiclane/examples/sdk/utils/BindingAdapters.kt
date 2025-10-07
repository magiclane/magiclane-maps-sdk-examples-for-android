/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.examples.sdk.utils

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMarginsRelative
import androidx.core.view.updatePadding
import androidx.databinding.BindingAdapter
import com.google.android.material.button.MaterialButton

@Suppress("DEPRECATION")
@BindingAdapter(
    "paddingLeftSystemWindowInsets",
    "paddingTopSystemWindowInsets",
    "paddingRightSystemWindowInsets",
    "paddingBottomSystemWindowInsets",
    requireAll = false
)
fun addSystemWindowInsetToPadding(
    view: View,
    leftPadding: Float?,
    topPadding: Float?,
    rightPadding: Float?,
    bottomPadding: Float?
) {
    view.doOnApplyWindowInsets { v, insets ->
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            left = systemBarsInsets.left
            top = systemBarsInsets.top
            right = systemBarsInsets.right
            bottom = systemBarsInsets.bottom
        } else {
            left = insets.systemWindowInsetLeft
            top = insets.systemWindowInsetTop
            right = insets.systemWindowInsetRight
            bottom = insets.systemWindowInsetBottom
        }

        v.updatePadding(
            left = leftPadding?.let { it.toInt() + left } ?: v.paddingLeft,
            top = topPadding?.let { it.toInt() + top } ?: v.paddingTop,
            right = rightPadding?.let { it.toInt() + right } ?: v.paddingRight,
            bottom = bottomPadding?.let { it.toInt() + bottom } ?: v.paddingBottom
        )
    }
}

@Suppress("DEPRECATION")
@BindingAdapter(
    "marginLeftSystemWindowInsets",
    "marginTopSystemWindowInsets",
    "marginRightSystemWindowInsets",
    "marginBottomSystemWindowInsets",
    requireAll = false
)
fun addSystemWindowInsetToMargin(
    view: View,
    leftSafeArea: Float?,
    topSafeArea: Float?,
    rightSafeArea: Float?,
    bottomSafeArea: Float?
) {
    view.doOnApplyWindowInsets { v, insets ->
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            left = systemBarsInsets.left
            top = systemBarsInsets.top
            right = systemBarsInsets.right
            bottom = systemBarsInsets.bottom
        }
        else
        {
            left = insets.systemWindowInsetLeft
            top = insets.systemWindowInsetTop
            right = insets.systemWindowInsetRight
            bottom = insets.systemWindowInsetBottom
        }
        v.updateLayoutParams {
            (this as? MarginLayoutParams)?.let {
                updateMarginsRelative(
                    start = leftSafeArea?.let { it.toInt() + left } ?: marginStart,
                    top = topSafeArea?.let { it.toInt() + top } ?: topMargin,
                    end = rightSafeArea?.let { it.toInt() + right } ?: marginEnd,
                    bottom = bottomSafeArea?.let { it.toInt() + bottom } ?: bottomMargin
                )
            }
        }
    }
}

@BindingAdapter("bottomMargin")
fun View.setBottomMargin(margin: Float) {
    updateLayoutParams { 
        (layoutParams as MarginLayoutParams).bottomMargin = margin.toInt()
    }
}

@BindingAdapter("iconResource")
fun MaterialButton.setIconResource(resource: Int?) {
    resource?.let { 
        this.icon = ContextCompat.getDrawable(context, resource)
    }
}

@BindingAdapter("layout_constraintVertical_bias")
fun setVerticalBias(view: View, bias: Float) {
 val params = view.layoutParams as? ConstraintLayout.LayoutParams ?: return
 params.verticalBias = bias
 view.layoutParams = params
}

 @BindingAdapter("setBackgroundColor")
 fun ImageView.setBgColor(color: Int) {
     this.setBackgroundColor(color)
 }

@BindingAdapter("app:setBitmap")
fun ImageView.setBitmap(bitmap: Bitmap?) {
    this.setImageBitmap(bitmap)
}

@BindingAdapter("android:visibility")
fun View.setIsVisible(visible: Boolean) {
    this.visibility = if (visible) View.VISIBLE else View.GONE
}

fun View.doOnApplyWindowInsets(f: (View, WindowInsets) -> Unit) {
    // Set an actual OnApplyWindowInsetsListener which proxies to the given
    // lambda, also passing in the original padding state
    setOnApplyWindowInsetsListener { v, insets ->
        f(v, insets)
        // Always return the insets, so that children can also use them
        insets
    }
    // request some insets
    requestApplyInsetsWhenAttached()
}

fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        // We're already attached, just request as normal
        requestApplyInsets()
    } else {
        // We're not attached to the hierarchy, add a listener to
        // request when we are
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}
