/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.displaycurrentstreetinfocompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.displaycurrentstreetinfocompose.ui.components.DisplayCurrentStreetInfoApp
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MagicLaneTheme {
                DisplayCurrentStreetInfoApp(Modifier.fillMaxSize())
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            GemSdk.release()
        }

        exitProcess(0)
    }
}
