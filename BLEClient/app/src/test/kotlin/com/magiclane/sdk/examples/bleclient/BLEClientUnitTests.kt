/*
 * Copyright (C) 2019-2024, Magic Lane B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Magic Lane
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with Magic Lane.
 */

package com.magiclane.sdk.examples.bleclient

import com.polidea.rxandroidble2.mockrxandroidble.RxBleClientMock
import io.reactivex.Observable
import org.junit.Test

import org.junit.Assert.*
import java.util.UUID

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class BLEClientUnitTests {

    companion object{
        val MOCK_SERVICE_UUID = ""
        val MOCK_WRITE_CHARACTERISTIC_UUID = ""
        val MOCK_INDICATE_CHARACTERISTIC_UUID = ""
        val CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = ""
    }

    val mockDevice = RxBleClientMock.DeviceBuilder()
        .deviceMacAddress("00:11:22:33:44:55")
        .deviceName("HR Monitor")
        .scanRecord(byteArrayOf(0x00))
        .rssi(-50)
        .addService(
            UUID.fromString(MOCK_SERVICE_UUID),
            RxBleClientMock.CharacteristicsBuilder()
                .addCharacteristic(
                    UUID.fromString(MOCK_WRITE_CHARACTERISTIC_UUID),
                    byteArrayOf(),
                    RxBleClientMock.DescriptorsBuilder().build()
                )
                .addCharacteristic(
                    UUID.fromString(MOCK_INDICATE_CHARACTERISTIC_UUID),
                    byteArrayOf(),
                    RxBleClientMock.DescriptorsBuilder()
                        .addDescriptor(
                            UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID),
                            byteArrayOf()
                        )
                        .build()
                )
                .build()
        )
        .notificationSource(
            UUID.fromString(MOCK_INDICATE_CHARACTERISTIC_UUID),
            Observable.just(
                byteArrayOf(0),
                byteArrayOf(1)
            )
        )
        .build()

}