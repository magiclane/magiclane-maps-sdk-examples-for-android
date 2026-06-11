/*
 * SPDX-FileCopyrightText: 2024-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.downloadingonboardmap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.util.SdkCall
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors
import kotlin.io.path.Path
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class DownloadingOnboardMapInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()

        @BeforeClass
        @JvmStatic
        fun setUp() {
            deleteMap()
        }

        @AfterClass
        @JvmStatic
        fun deleteResources() {
            deleteMap()
        }

        private fun deleteMap() {
            appContext.getExternalFilesDirs(null)?.forEach {
                val pathInt = it.path.toString() + File.separator + "Data" + File.separator + "Maps"
                val stream = Files.find(Path(pathInt), 20, { filePath, _ ->
                    filePath.fileName.toString().startsWith("Lux")
                })
                val list = stream.collect(Collectors.toList())
                list.forEach { itemList ->
                    if (Files.exists(itemList)) {
                        Files.delete(itemList)
                    }
                }
            }
        }
    }

    @Test
    fun downloadResource() = runBlocking {
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

                    for (map in contentListPair.first) {
                        val mapName = map.name ?: continue
                        if (mapName.compareTo(luxembourg, true) != 0) {
                            // searching another map
                            continue
                        }

                        if (!map.isCompleted()) {
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
                                onCompleted = { errorCode, msg ->
                                    assert(!GemError.isError(errorCode)) { msg }
                                    runBlocking {
                                        delay(10000)
                                        channel.send(Unit)
                                    }
                                },
                            )
                            // Start downloading the first map item.
                            map.asyncDownload(
                                downloadProgressListener,
                                GemSdk.EDataSavePolicy.UseDefault,
                                true,
                            )
                        }
                        break
                    }
                }
            },
        )

        SdkCall.execute {
            // Call to the content store to asynchronously retrieve the list of maps.
            contentStore.asyncGetStoreContentList(EContentType.RoadMap, contentListener)
        }

        withTimeout(120000) {
            channel.receive()
        }
    }
}
