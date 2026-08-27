/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblockcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.magiclane.sdk.compose.theme.MagicLaneTheme
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.definepersistentroadblockcompose.ui.components.DefineRoadblockApp
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MagicLaneTheme {
                DefineRoadblockApp(Modifier.fillMaxSize())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isFinishing) {
            GemSdk.release()
        }

        // exitProcess is required because the SDK holds native threads that do not stop on
        // their own when the Activity is destroyed, which would leave the process alive.
        exitProcess(0)
    }
}
