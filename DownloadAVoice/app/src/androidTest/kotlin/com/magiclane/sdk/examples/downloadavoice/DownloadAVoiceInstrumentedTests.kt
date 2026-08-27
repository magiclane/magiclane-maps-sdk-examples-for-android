/*
 * SPDX-FileCopyrightText: 2023-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.downloadavoice

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class DownloadAVoiceInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule(order = 0)
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    @Test
    fun requestVoiceAndDownload() = runBlocking {
        val channel = Channel<Unit>()
        var count = 0
        val contentStore = ContentStore()
        var errorAsyncDownload: ErrorCode = GemError.General
        var progressErrorCode: ErrorCode = GemError.General
        val progressListener = ProgressListener.create(
            onCompleted = onCompletedFun@{ errorCode, _ ->
                progressErrorCode = errorCode
                if (errorCode != GemError.NoError) {
                    runBlocking { channel.send(Unit) }
                    return@onCompletedFun
                }
                SdkCall.execute {
                    // No error encountered, we can handle the results.
                    // Get the list of voices that was retrieved in the content store.
                    val models =
                        contentStore.getStoreContentList(EContentType.HumanVoice)?.first
                    if (!models.isNullOrEmpty()) {
                        // The voice items list is not empty or null.
                        val voiceItem = models[0]

                        // Start downloading the first voice item.
                        runBlocking {
                            errorAsyncDownload = async {
                                SdkCall.execute {
                                    voiceItem.asyncDownload(
                                        GemSdk.EDataSavePolicy.UseDefault,
                                        true,
                                        onStarted = {
                                        },
                                        onCompleted = { _, _ ->
                                            count++
                                            runBlocking {
                                                channel.send(Unit)
                                            }
                                        },
                                        onProgress = {
                                        },
                                    )
                                } ?: GemError.General
                            }.await()
                        }
                    }
                }
            },
            postOnMain = true,
        )

        val error = async {
            SdkCall.execute {
                // Defines an action that should be done after the network is connected.
                // Call to the content store to asynchronously retrieve the list of voices.
                contentStore.asyncGetStoreContentList(
                    EContentType.HumanVoice,
                    progressListener,
                )
            } ?: GemError.General
        }.await()

        withTimeout(300000) {
            channel.receive()
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(
                errorAsyncDownload == GemError.NoError,
            ) { GemError.getMessage(errorAsyncDownload) }
            assert(progressErrorCode == GemError.NoError) { GemError.getMessage(progressErrorCode) }
        }
    }
}
