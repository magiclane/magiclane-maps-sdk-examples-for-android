/*
 * Copyright (C) 2019-2022, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

package com.generalmagic.sdk.examples.androidauto.androidAuto.controllers

import androidx.car.app.CarContext
import com.generalmagic.sdk.core.ErrorCode
import com.generalmagic.sdk.core.GemError
import com.generalmagic.sdk.examples.androidauto.androidAuto.model.UIActionModel
import com.generalmagic.sdk.examples.androidauto.androidAuto.screens.DialogScreen

class ErrorDialogController(context: CarContext, val error: ErrorCode) : DialogScreen(context) {
    override fun updateData() {
        title = "Oops.."
        headerAction = UIActionModel.backModel()
        message = GemError.getMessage(error)
    }
}