/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.markerclustering

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStreamReader

/**
 * Parses the bundled assets/campsites.geojson into [CampsiteMarkerDescriptor]
 * values. Only the fields the map needs are decoded; everything else is skipped.
 * Uses [JsonReader] to stream the multi-MB file instead of loading the whole DOM
 * into memory. Must be called off the main thread.
 */
object CampsiteGeoJSONLoader {

    fun loadFromAssets(context: Context, assetName: String = "campsites.geojson"): List<CampsiteMarkerDescriptor> =
        runCatching {
            context.assets.open(assetName).use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    parse(reader)
                }
            }
        }.getOrElse { emptyList() }

    private fun parse(reader: JsonReader): List<CampsiteMarkerDescriptor> {
        val result = mutableListOf<CampsiteMarkerDescriptor>()
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "features") {
                reader.beginArray()
                while (reader.hasNext()) {
                    readFeature(reader)?.let(result::add)
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return result
    }

    private fun readFeature(reader: JsonReader): CampsiteMarkerDescriptor? {
        var longitude: Double? = null
        var latitude: Double? = null
        var campsiteId: Int? = null
        var name: String? = null
        var bookable = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "geometry" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "coordinates") {
                            reader.beginArray()
                            if (reader.hasNext()) longitude = reader.nextDouble()
                            if (reader.hasNext()) latitude = reader.nextDouble()
                            while (reader.hasNext()) reader.skipValue()
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "properties" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "campsiteId" -> campsiteId = reader.nextInt()
                            "name" -> name = reader.nextStringOrNull()
                            "bookable" -> bookable = reader.nextBooleanOrFalse()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val id = campsiteId ?: return null
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return CampsiteMarkerDescriptor(
            id = id,
            name = name ?: "",
            isBookable = bookable,
            latitude = lat,
            longitude = lon,
        )
    }

    private fun JsonReader.nextStringOrNull(): String? = if (peek() == JsonToken.NULL) {
        skipValue()
        null
    } else {
        nextString()
    }

    private fun JsonReader.nextBooleanOrFalse(): Boolean = if (peek() == JsonToken.NULL) {
        skipValue()
        false
    } else {
        nextBoolean()
    }
}
