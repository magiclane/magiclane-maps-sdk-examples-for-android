/*
 * Copyright (C) 2019-2021, General Magic B.V.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of General Magic
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with General Magic.
 */

@file:Suppress("UNUSED_PARAMETER", "MemberVisibilityCanBePrivate", "unused")

package com.generalmagic.sdk.examples.demo.activities.settings

import android.content.Intent
import com.generalmagic.sdk.core.EUnitSystem
import com.generalmagic.sdk.examples.demo.app.GEMApplication
import com.generalmagic.sdk.examples.demo.util.Utils
import com.generalmagic.sdk.routesandnavigation.ERouteType
import com.generalmagic.sdk.util.SdkCall
import com.generalmagic.sdk.util.StringIds
import com.generalmagic.sdk.util.SdkUtil.getUIString

enum class TSettingItemType(val value: Int) {
    EBoolean(0),
    EInt(1),
    EDouble(2),
    EText(3),
    EOptionsList(4);
}

abstract class CSettingItem(protected var m_setting: Int) {
    open fun getText() = ""
    open fun getBoolValue() = true
    open fun getIntValue() = 0
    open fun getIntTextValue() = ""
    open fun getIntMinValue() = 0
    open fun getIntMinTextValue() = ""
    open fun getIntMaxValue() = 0
    open fun getIntMaxTextValue() = ""
    open fun getDoubleValue() = 0.0
    open fun getDoubleTextValue() = ""
    open fun getDoubleMinValue() = 0.0
    open fun getDoubleMinTextValue() = ""
    open fun getDoubleMaxValue() = 0.0
    open fun getDoubleMaxTextValue() = ""
    open fun getOptionsListCount() = 0
    open fun getOptionsListText(index: Int) = ""
    open fun getOptionsListSelectedItemIndex() = 0
    open fun didTapOptionsListItem(index: Int) {}
    open fun didChooseNewBoolValue(value: Boolean) {}
    open fun didChooseNewIntValue(value: Int) {}
    open fun didChooseNewDoubleValue(value: Double) {}
    abstract fun getType(): TSettingItemType
}

open class CBoolItem(val m_text: String, setting: Int) : CSettingItem(setting) {
    override fun getType() = TSettingItemType.EBoolean
    override fun getText(): String = m_text
    override fun getBoolValue(): Boolean = SdkCall.execute {
        SettingsProvider.getBooleanValue(m_setting).second
    } ?: false

    override fun didChooseNewBoolValue(value: Boolean) {
        SdkCall.execute { SettingsProvider.setBooleanValue(m_setting, value) }
    }
}

open class CIntItem(val m_text: String, setting: Int, val m_min: Int, val m_max: Int) :
    CSettingItem(setting) {
    override fun getType() = TSettingItemType.EInt
    override fun getText(): String = m_text
    override fun getIntValue() = SdkCall.execute {
        SettingsProvider.getIntValue(m_setting).second
    } ?: 0

    override fun getIntTextValue() = String.format("%d", getIntValue())
    override fun getIntMinValue() = m_min
    override fun getIntMinTextValue() = String.format("%d", m_min)
    override fun getIntMaxValue() = m_max
    override fun getIntMaxTextValue() = String.format("%d", m_max)
    override fun didChooseNewIntValue(value: Int) {
        SdkCall.execute { SettingsProvider.setIntValue(m_setting, value) }
    }
}

class CInfinityIntSetting(
    m_text: String, setting: Int, m_min: Int, m_max: Int, val m_infinity: Int
) :
    CIntItem(m_text, setting, m_min, m_max) {
    override fun getIntTextValue(): String {
        if (getIntValue() == m_infinity) return "∞"
        return super.getIntTextValue()
    }

    override fun getIntMaxTextValue(): String {
        if (getIntMaxValue() == m_infinity) return "∞"
        return super.getIntMaxTextValue()
    }
}

open class COptionsListItem(val m_text: String, setting: Int) : CSettingItem(setting) {
    val mOptions = ArrayList<Pair<String, Int>>()

    override fun getType() = TSettingItemType.EOptionsList
    override fun getText(): String = m_text
    override fun getOptionsListCount() = mOptions.size
    override fun getOptionsListText(index: Int) = mOptions[index].first
    override fun getOptionsListSelectedItemIndex(): Int {
        val setting = SdkCall.execute { SettingsProvider.getIntValue(m_setting) }
        for (i in 0 until mOptions.size) {
            if (mOptions[i].second == setting?.second)
                return i
        }
        return -1
    }

    override fun didTapOptionsListItem(index: Int) {
        SdkCall.execute { SettingsProvider.setIntValue(m_setting, mOptions[index].second) }
    }
}

class TravelModeCarOptions(m_text: String, setting: Int) : COptionsListItem(m_text, setting) {
    init {
        mOptions.add(
            Pair(getUIString(StringIds.eStrFastest), ERouteType.Fastest.value)
        )
        mOptions.add(
            Pair(getUIString(StringIds.eStrShortest), ERouteType.Shortest.value)
        )
    }
}

class UnitsSystemOptions(m_text: String, setting: Int) : COptionsListItem(m_text, setting) {
    init {
        mOptions.add(
            Pair(getUIString(StringIds.eStrInKilometres), EUnitSystem.Metric.value)
        )
        mOptions.add(
            Pair(
                getUIString(StringIds.eStrInMilesFeet),
                EUnitSystem.ImperialUs.value
            )
        )
        mOptions.add(
            Pair(
                getUIString(StringIds.eStrInMilesYards),
                EUnitSystem.ImperialUk.value
            )
        )
    }
}

interface ISettingsView {
    fun refreshItemState(chapter: Int, index: Int, enabled: Boolean)
}

object GEMSettingsView {
    var m_chapters = ArrayList<Pair<String, ArrayList<CSettingItem>>>()
    var mView: ISettingsView? = null

    const val mMobileDataChapterIndex = 0
    const val mUseMobileDataItemIndex = 0
    const val mUseMobileDataForMapAndWikipedia = 2
    const val mTerrainAndSatelliteItemIndex = 3

    private val settingsActivitiesMap: HashMap<Long, SettingsActivity> = HashMap()

    fun registerActivity(viewId: Long, settingsActivity: SettingsActivity) {
        settingsActivitiesMap[viewId] = settingsActivity
        mView = settingsActivity
    }

    private fun unregisterActivity(viewId: Long) {
        settingsActivitiesMap.remove(viewId)
    }

    private fun open(viewId: Long) {
        GEMApplication.postOnMain {
            val intent =
                Intent(GEMApplication.applicationContext(), SettingsActivity::class.java)
            intent.putExtra("viewId", viewId)
            GEMApplication.topActivity()?.startActivity(intent)
        }
    }

    private fun close(viewId: Long) {
        GEMApplication.postOnMain {
            if (settingsActivitiesMap.containsKey(viewId)) {
                settingsActivitiesMap[viewId]?.finish()
            }
        }
    }

    private fun refreshItemState(viewId: Long, chapter: Int, index: Int, enabled: Boolean) {
        GEMApplication.postOnMain {
            settingsActivitiesMap[viewId]?.refreshItemState(chapter, index, enabled)
        }
    }

    fun onViewClosed(viewId: Long) {
        unregisterActivity(viewId)

        SdkCall.execute {
            didCloseView(viewId)
        }
    }

    init {
        // mobile data
        val mapDataAndWikipedia = String.format(
            "%s / %s",
            getUIString(StringIds.eStrMapData),
            getUIString(StringIds.eStrWikipedia)
        )

        m_chapters.add(
            Pair(
                getUIString(StringIds.eStrMapData), arrayListOf(
                    CBoolItem(
                        getUIString(StringIds.eStrUseMobileDataConnection),
                        TBoolSettings.EUseMobileData.value
                    ),
                    CBoolItem(
                        getUIString(StringIds.eStrTraffic),
                        TBoolSettings.EUseMobileDataForTraffic.value
                    ),
                    CBoolItem(
                        mapDataAndWikipedia,
                        TBoolSettings.EUseMobileDataForMapAndWikipedia.value
                    ),
                    CBoolItem(
                        getUIString(StringIds.eStrTerrainAndSatellite),
                        TBoolSettings.EUseMobileDataForTerrainAndSatellite.value
                    )
                )
            )
        )

        // navigation

        m_chapters.add(
            Pair(
                getUIString(StringIds.eStrNavigation), arrayListOf(
                    CIntItem(
                        getUIString(StringIds.eStrSimulationSpeed),
                        TIntSettings.EDemoSpeed.value, 1, 10
                    ),
                    UnitsSystemOptions(
                        getUIString(StringIds.eStrDistances),
                        TIntSettings.EUnitsSystem.value
                    ),
                    TravelModeCarOptions(
                        getUIString(StringIds.eStrTravelMode),
                        TIntSettings.ETravelModeCar.value
                    ),
                    CBoolItem(
                        getUIString(StringIds.eStrAvoidMotorways),
                        TBoolSettings.EAvoidMotorwaysCar.value
                    ),
                    CBoolItem(
                        getUIString(StringIds.eStrAvoidTollRoads),
                        TBoolSettings.EAvoidTollRoadsCar.value
                    ),
                    CBoolItem(
                        getUIString(StringIds.eStrAvoidFerries),
                        TBoolSettings.EAvoidFerriesCar.value
                    ),
                    CBoolItem(
                        getUIString(StringIds.eStrAvoidUnpavedRoads),
                        TBoolSettings.EAvoidUnpavedRoadsCar.value
                    )

                )
            )
        )

        // Recorder

        m_chapters.add(
            Pair(
                getUIString(StringIds.eStrRecording), arrayListOf(
                    CBoolItem(
                        "Record audio",
                        TBoolSettings.ERecordAudio.value
                    ),

                    CInfinityIntSetting(
                        "Log length (min)",
                        TIntSettings.ERecordingChunk.value, 1, 61, 61
                    ),

                    CInfinityIntSetting(
                        "Avoid auto delete recent logs (min)",
                        TIntSettings.EMinMinutes.value, 1, 61, 61
                    ),

                    CInfinityIntSetting(
                        "Recording storage limit (MB)",
                        TIntSettings.EDiskLimit.value, 300, 2001, 2001
                    )
                )
            )
        )
    }

    fun getTitle(viewId: Long): String = getUIString(StringIds.eStrSettings)

    fun getChaptersCount(viewId: Long): Int = m_chapters.size

    fun getItemsCount(viewId: Long, chapter: Int): Int = m_chapters[chapter].second.size

    fun getChapterText(viewId: Long, chapter: Int): String = m_chapters[chapter].first

    fun getItemType(viewId: Long, chapter: Int, index: Int): Int =
        m_chapters[chapter].second[index].getType().value

    fun getItemText(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getText()

    fun getItemDescription(viewId: Long, chapter: Int, index: Int): String = ""

    fun getBoolValue(viewId: Long, chapter: Int, index: Int): Boolean =
        m_chapters[chapter].second[index].getBoolValue()

    fun getIntValue(viewId: Long, chapter: Int, index: Int): Int =
        m_chapters[chapter].second[index].getIntValue()

    fun getIntTextValue(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getIntTextValue()

    fun getIntMinValue(viewId: Long, chapter: Int, index: Int): Int =
        m_chapters[chapter].second[index].getIntMinValue()

    fun getIntMinTextValue(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getIntMinTextValue()

    fun getIntMaxValue(viewId: Long, chapter: Int, index: Int): Int =
        m_chapters[chapter].second[index].getIntMaxValue()

    fun getIntMaxTextValue(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getIntMaxTextValue()

    fun getOptionsListCount(viewId: Long, chapter: Int, index: Int): Int =
        m_chapters[chapter].second[index].getOptionsListCount()

    fun getDoubleValue(viewId: Long, chapter: Int, index: Int): Double =
        m_chapters[chapter].second[index].getDoubleValue()

    fun getDoubleTextValue(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getDoubleTextValue()

    fun getDoubleMinValue(viewId: Long, chapter: Int, index: Int): Double =
        m_chapters[chapter].second[index].getDoubleMinValue()

    fun getDoubleMinTextValue(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getDoubleMinTextValue()

    fun getDoubleMaxValue(viewId: Long, chapter: Int, index: Int): Double =
        m_chapters[chapter].second[index].getDoubleMaxValue()

    fun getDoubleMaxTextValue(viewId: Long, chapter: Int, index: Int): String =
        m_chapters[chapter].second[index].getDoubleMaxTextValue()


    fun getOptionsListText(
        viewId: Long,
        chapter: Int,
        index: Int,
        optionsListIndex: Int
    ): String =
        m_chapters[chapter].second[index].getOptionsListText(optionsListIndex)

    fun getOptionsListSelectedItemIndex(viewId: Long, chapter: Int, index: Int): Int =
        m_chapters[chapter].second[index].getOptionsListSelectedItemIndex()

    fun isItemEnabled(viewId: Long, chapter: Int, index: Int): Boolean {
        if ((chapter == mMobileDataChapterIndex) && (index > 0)) // mobile data chapter
        {
            val bUseMobileData = SdkCall.execute {
                return@execute SettingsProvider.getBooleanValue(TBoolSettings.EUseMobileData.value).second
            } ?: false

            if (!bUseMobileData) {
                return false
            } else {
                if (index == mTerrainAndSatelliteItemIndex) // Terrain + Satellite option
                {
                    return SdkCall.execute {
                        return@execute SettingsProvider.getBooleanValue(TBoolSettings.EUseMobileDataForMapAndWikipedia.value).second
                    } ?: false
                }
            }
        }
        return true
    }

    fun didTapItem(viewId: Long, chapter: Int, index: Int) {}

    fun didTapOptionsListItem(
        viewId: Long,
        chapter: Int,
        index: Int,
        optionsListIndex: Int
    ) = m_chapters[chapter].second[index].didTapOptionsListItem(optionsListIndex)

    fun didChooseNewBoolValue(viewId: Long, chapter: Int, index: Int, value: Boolean) {
        if (chapterIndexIsValid(chapter, index)) {
            m_chapters[chapter].second[index].didChooseNewBoolValue(value)

            val mView = this.mView
            if ((chapter == mMobileDataChapterIndex) && mView != null) {
                if (index == mUseMobileDataItemIndex) {
                    for (i in 1 until m_chapters[chapter].second.size) {
                        if (i == mTerrainAndSatelliteItemIndex && value) {
                            val bUseMobileDataForMapAndWikipedia = SdkCall.execute {
                                return@execute SettingsProvider.getBooleanValue(TBoolSettings.EUseMobileDataForMapAndWikipedia.value).second
                            } ?: false

                            if (bUseMobileDataForMapAndWikipedia) {
                                mView.refreshItemState(chapter, i, true)
                            }
                        } else {
                            mView.refreshItemState(chapter, i, value)
                        }
                    }
                } else if ((index == mUseMobileDataForMapAndWikipedia) &&
                    (mTerrainAndSatelliteItemIndex < m_chapters[chapter].second.size)
                ) {
                    mView.refreshItemState(chapter, mTerrainAndSatelliteItemIndex, value)
                }
            }
        }
    }

    fun didChooseNewIntValue(viewId: Long, chapter: Int, index: Int, value: Int) =
        m_chapters[chapter].second[index].didChooseNewIntValue(value)

    fun didChooseNewDoubleValue(viewId: Long, chapter: Int, index: Int, value: Double) =
        m_chapters[chapter].second[index].didChooseNewDoubleValue(value)

    fun didCloseView(viewId: Long) {}

    fun chapterIndexIsValid(chapter: Int, index: Int): Boolean {
        return (chapter >= 0) &&
            (chapter < m_chapters.size) &&
            (index >= 0) &&
            (index < m_chapters[chapter].second.size)
    }
}
