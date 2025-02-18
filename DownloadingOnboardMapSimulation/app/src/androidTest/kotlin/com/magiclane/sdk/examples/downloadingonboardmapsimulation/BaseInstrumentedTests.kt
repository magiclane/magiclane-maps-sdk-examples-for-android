// -------------------------------------------------------------------------------------------------------------------------------

/*
 * SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Contact Magic Lane at <info@magiclane.com> for commercial licensing options.
 */

// -------------------------------------------------------------------------------------------------------------------------------

package com.magiclane.sdk.examples.downloadingonboardmapsimulation

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors
import kotlin.io.path.Path

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class BaseInstrumentedTests
{
    companion object
    {
        const val TIMEOUT = 600000L
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var initResult = false

        @get:ClassRule
        @JvmStatic
        val sdkInitRule = SDKInitRule()

        @BeforeClass
        @JvmStatic
        fun checkSdkInitStartActivity()
        {
            assert(initResult) { "GEM SDK not initialized" }
            deleteMap()
        }

        @AfterClass
        @JvmStatic
        fun deleteResources()
        {
            deleteMap()
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

    @Before
    fun checkInternetAndToken(){
        //verify token and internet connection
        SdkCall.execute { assert(GemSdk.getTokenFromManifest(appContext)?.isNotEmpty() == true) { "Invalid token." } }
        assert(appContext.getSystemService(ConnectivityManager::class.java).activeNetwork != null) { " No internet connection." }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------
    class SDKInitRule : TestRule
    {
        override fun apply(base: Statement, description: Description) = SDKStatement(base)

        inner class SDKStatement(private val base: Statement) : Statement()
        {
            private val channel = Channel<Boolean>()

            init
            {
                SdkSettings.onMapDataReady = { isReady ->
                    if (isReady)
                        runBlocking {
                            channel.send(true)
                        }
                }
            }

            @Throws(Throwable::class)
            override fun evaluate()
            {
                //before tests are executed
                if (!GemSdk.isInitialized())
                {
                    runBlocking {
                        initResult = GemSdk.initSdkWithDefaults(appContext)
                        // must wait for map data ready
                        withTimeout(TIMEOUT) {
                            channel.receive()
                        }
                    }
                }
                else return

                if (!SdkSettings.isMapDataReady)
                    throw Error(GemError.getMessage(GemError.OperationTimeout))

                try
                {
                    base.evaluate() // This executes tests
                }
                finally
                {
                    GemSdk.release()
                }
            }
        }
    }
    // -------------------------------------------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------

    @Test
    fun downloadResource()  = runBlocking{
        val channel = Channel<Unit>()
        val luxembourg = "Luxembourg"
        val contentStore = ContentStore()
        val contentListener = ProgressListener.create(
            onStarted = {
            },
            onCompleted = { errorCode, _ ->
                assert(!GemError.isError(errorCode)) { GemError.getMessage(errorCode) }
                // No error encountered, we can handle the results.
                SdkCall.execute { // Get the list of maps that was retrieved in the content store.
                    val contentListPair =
                        contentStore.getStoreContentList(EContentType.RoadMap) ?: return@execute

                    for (map in contentListPair.first)
                    {
                        val mapName = map.name ?: continue
                        if (mapName.compareTo(luxembourg, true) != 0) // searching another map
                            continue

                        if (!map.isCompleted())
                        {
                            // Define a listener to the progress of the map download action.
                            val downloadProgressListener = ProgressListener.create(
                                onStarted = {
                                    SdkCall.execute {
                                        map.countryCodes?.let { codes ->
                                            val size =
                                                appContext.resources.getDimension(R.dimen.icon_size)
                                                    .toInt()
                                            val flagBitmap = MapDetails().getCountryFlag(codes[0])
                                                ?.asBitmap(size, size)
                                            assert(flagBitmap != null)
                                        }
                                    }
                                },
                                onCompleted = { err , msg ->
                                    assert(!GemError.isError(err)){"error on onCOmpleted: $msg"}
                                    runBlocking {
                                        delay(10000)
                                        channel.send(Unit)
                                    }
                                })
                            // Start downloading the first map item.
                            map.asyncDownload(
                                downloadProgressListener,
                                GemSdk.EDataSavePolicy.UseDefault,
                                true
                            )
                        }
                        break
                    }
                }
            }
        )

        SdkCall.execute {
            // Call to the content store to asynchronously retrieve the list of maps.
            contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
        }

        withTimeout(120000){
            channel.receive()
        }
    }
}
