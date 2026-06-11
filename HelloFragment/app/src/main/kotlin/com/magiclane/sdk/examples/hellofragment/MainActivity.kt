/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellofragment

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.examples.hellofragment.databinding.ActivityMainBinding
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep status-bar icons light against the dark primary toolbar background.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
        exitProcess(0)
    }
}
