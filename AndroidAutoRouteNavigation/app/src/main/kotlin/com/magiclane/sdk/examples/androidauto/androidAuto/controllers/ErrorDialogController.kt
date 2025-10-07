/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.magiclane.sdk.examples.androidauto.androidAuto.screens.DialogScreen

class ErrorDialogController(context: CarContext, val error: ErrorCode) : DialogScreen(context) {
    override fun updateData() {
        title = "Oops.."
        headerAction = UIActionModel.backModel()
        message = GemError.getMessage(error)
    }
}