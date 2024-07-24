// -------------------------------------------------------------------------------------------------------------------------------

/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

// -------------------------------------------------------------------------------------------------------------------------------


package com.magiclane.sdk.examples.downloadingonboardmapsimulation

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.io.path.Path

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UITestsForDownloadingViews
{
    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)
    private val contentStore = ContentStore()
    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun registerIdlingResource(): Unit = runBlocking {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        delay(2000)
        deleteMap()
        IdlingRegistry.getInstance().register(EspressoIdlingResource.downloadingIdlingResource)
        IdlingPolicies.setIdlingResourceTimeout(60000, TimeUnit.MILLISECONDS)
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }

    @After
    fun closeActivity()
    {
        SdkCall.execute {
            val list = contentStore.getLocalContentList(EContentType.RoadMap)
            list?.forEach {
                if (it.canDeleteContent()) it.deleteContent()
            }
        }
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.downloadingIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun t1_checkDownloadingViews(): Unit = runBlocking {
        deleteMap()
        onView(ViewMatchers.withId(R.id.flag_icon)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        onView(ViewMatchers.withId(R.id.download_progress_bar)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    private fun deleteMap()
    {
        appContext.getExternalFilesDirs(null)?.forEach {
            val pathInt = it.path.toString() + File.separator + "Data" + File.separator + "Maps"
            val stream = Files.find(Path(pathInt), 20, { filePath, _ ->
                filePath.fileName.toString().startsWith("Lux")
            })
            val list = stream.collect(Collectors.toList())
            list.forEach { itemList ->
                if (Files.exists(itemList))
                    Files.delete(itemList)
            }
        }
    }
}
