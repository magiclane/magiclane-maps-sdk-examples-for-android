// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.basicshapedrawer

// -------------------------------------------------------------------------------------------------------------------------------

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

// -------------------------------------------------------------------------------------------------------------------------------

class MainActivity : AppCompatActivity()
{
    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SdkSettings.onApiTokenRejected = {
            /*
            The TOKEN you provided in the AndroidManifest.xml file was rejected.
            Make sure you provide the correct value, or if you don't have a TOKEN,
            check the magiclane.com website, sign up/sign in and generate one. 
             */
            Utils.showDialog("TOKEN REJECTED", this)
        }

        if (!Util.isInternetConnected(this))
        {
            Utils.showDialog("You must be connected to the internet!", this)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true)
        {
            override fun handleOnBackPressed()
            {
                finish()
                exitProcess(0)
            }
        })
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.navigation_menu, menu)
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------------
    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        // Handle item selection.
        return when (item.itemId)
        {
            R.id.go_to_population_fragment ->
            {
                findNavController(R.id.fr_container).apply {
                    currentDestination?.let {
                        if (it.id != R.id.population)
                            navigate(R.id.action_speed_limits_panel_to_population)
                    }
                }
                true
            }

            R.id.go_to_speed_limits ->
            {
                findNavController(R.id.fr_container).apply {
                    currentDestination?.let {
                        if (it.id != R.id.speed_limits_panel)
                            navigate(R.id.action_population_to_dataVisualization)
                    }
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
    // ---------------------------------------------------------------------------------------------------------------------------

    override fun onDestroy()
    {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    // ---------------------------------------------------------------------------------------------------------------------------
}

// -------------------------------------------------------------------------------------------------------------------------------
