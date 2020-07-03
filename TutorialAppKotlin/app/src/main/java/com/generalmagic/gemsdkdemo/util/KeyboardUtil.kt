// Copyright (C) 2019-2020, General Magic B.V.
// All rights reserved.
//
// This software is confidential and proprietary information of General Magic
// ("Confidential Information"). You shall not disclose such Confidential
// Information and shall use it only in accordance with the terms of the
// license agreement you entered into with General Magic.

package com.generalmagic.gemsdkdemo.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager


class KeyboardUtil {
	companion object {
		fun hideKeyboard(context: Context?) {
			hideKeyboard((context as Activity?))
		}

		fun hideKeyboard(activity: Activity?) {
			if (activity == null) return

			val imm: InputMethodManager =
				activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

			//Find the currently focused view, so we can grab the correct window token from it.
			var view: View? = activity.currentFocus

			//If no view currently has focus, create a new one, just so we can grab a window token from it
			if (view == null) {
				view = View(activity)
			}
			imm.hideSoftInputFromWindow(view.windowToken, 0)
		}

		fun showKeyboard(activity: Activity, textInput: View) {
			if (textInput.requestFocus()) {
				val imm: InputMethodManager =
					activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

				imm.showSoftInput(textInput, InputMethodManager.SHOW_IMPLICIT)

//			activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
			}
		}
	}
}
