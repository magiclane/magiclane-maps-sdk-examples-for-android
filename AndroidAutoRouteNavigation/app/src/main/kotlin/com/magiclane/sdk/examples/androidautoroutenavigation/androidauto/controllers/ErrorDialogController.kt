/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.androidautoroutenavigation.R
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.model.UIActionModel
import com.magiclane.sdk.examples.androidautoroutenavigation.androidauto.screens.DialogScreen
import com.magiclane.sdk.util.SdkCall

class ErrorDialogController(context: CarContext, val error: ErrorCode) : DialogScreen(context) {
    override fun updateData() {
        title = context.getString(R.string.error)
        headerAction = UIActionModel.backModel()
        message = SdkCall.runSynced { GemError.getMessage(error, context) } ?: ""
    }
}
