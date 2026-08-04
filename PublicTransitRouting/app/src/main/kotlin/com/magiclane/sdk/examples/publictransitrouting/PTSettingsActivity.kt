/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.publictransitrouting

import android.os.Bundle
import android.widget.CompoundButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.magiclane.sdk.examples.publictransitrouting.databinding.ActivitySettingsBinding
import com.magiclane.sdk.routesandnavigation.EPTSortingStrategy
import java.util.Calendar
import java.util.TimeZone

/**
 * Public-transport settings, mirroring Magic Earth's settings screen: date & time anchor,
 * travel mode, min transfer time, preferred means of transport and accessibility.
 * If anything changed when the screen closes, a route recalculation is triggered.
 */
class PTSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var openingSnapshot: List<Any?>

    private val travelModes = listOf(
        EPTSortingStrategy.BestTime to R.string.best_route,
        EPTSortingStrategy.LeastWalk to R.string.less_walking,
        EPTSortingStrategy.LeastTransfers to R.string.fewer_transfers,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openingSnapshot = PTSettings.snapshot()

        binding.backButton.setOnClickListener { finish() }

        initDateTimeSection()
        initGeneralSection()
        initSwitches()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Changes are applied on close with a single recalculation (as in Magic Earth).
        if (isFinishing && PTSettings.snapshot() != openingSnapshot) {
            PTRouteSession.controller?.recalculate()
        }
    }

    // region date & time

    private fun initDateTimeSection() {
        binding.timeModeGroup.check(
            when (PTSettings.timeMode) {
                PTSettings.TimeMode.Depart -> R.id.mode_depart
                PTSettings.TimeMode.Arrive -> R.id.mode_arrive
                PTSettings.TimeMode.Last -> R.id.mode_last
            },
        )

        binding.timeModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.mode_depart -> PTSettings.timeMode = PTSettings.TimeMode.Depart
                R.id.mode_arrive -> PTSettings.timeMode = PTSettings.TimeMode.Arrive
                R.id.mode_last -> {
                    // "Last" means the last connection of today: arrive by 23:59.
                    PTSettings.timeMode = PTSettings.TimeMode.Last
                    PTSettings.customTimeMillis = WallClock.endOfTodayMillis()
                }
            }
            updateTimeButtons()
        }

        binding.nowButton.setOnClickListener {
            PTSettings.customTimeMillis = null
            updateTimeButtons()
        }

        binding.timeButton.setOnClickListener { showTimePicker() }
        binding.dateButton.setOnClickListener { showDatePicker() }

        updateTimeButtons()
    }

    private fun currentAnchorMillis(): Long = PTSettings.customTimeMillis ?: WallClock.nowMillis()

    private fun updateTimeButtons() {
        val millis = currentAnchorMillis()
        binding.timeButton.text = WallClock.timeText(millis)
        binding.dateButton.text = WallClock.dateText(millis)

        val editable = PTSettings.timeMode != PTSettings.TimeMode.Last
        binding.nowButton.isEnabled = editable
        binding.timeButton.isEnabled = editable
        binding.dateButton.isEnabled = editable
    }

    private fun showTimePicker() {
        val millis = currentAnchorMillis()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(WallClock.hour(millis))
            .setMinute(WallClock.minute(millis))
            .build()
        picker.addOnPositiveButtonClickListener {
            PTSettings.customTimeMillis = WallClock.withTime(millis, picker.hour, picker.minute)
            updateTimeButtons()
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    private fun showDatePicker() {
        val millis = currentAnchorMillis()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(millis) // wall-clock UTC epoch matches the picker's UTC selection
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selection }
            PTSettings.customTimeMillis = WallClock.withDate(
                millis,
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH),
            )
            updateTimeButtons()
        }
        picker.show(supportFragmentManager, "date_picker")
    }

    // endregion

    // region general

    private fun initGeneralSection() {
        updateTravelModeValue()
        binding.travelModeRow.setOnClickListener { showTravelModeDialog() }

        binding.minTransferMin.text =
            getString(R.string.minutes_format, binding.minTransferSlider.valueFrom.toInt())
        binding.minTransferMax.text =
            getString(R.string.minutes_format, binding.minTransferSlider.valueTo.toInt())

        binding.minTransferSlider.value = PTSettings.minTransferTimeMinutes.toFloat()
        updateMinTransferLabel()
        binding.minTransferSlider.addOnChangeListener { _, value, _ ->
            PTSettings.minTransferTimeMinutes = value.toInt()
            updateMinTransferLabel()
        }
    }

    private fun updateTravelModeValue() {
        val labelRes = travelModes.first { it.first == PTSettings.sortingStrategy }.second
        binding.travelModeValue.setText(labelRes)
    }

    private fun updateMinTransferLabel() {
        binding.minTransferCurrent.text =
            getString(R.string.minutes_format, PTSettings.minTransferTimeMinutes)
    }

    private fun showTravelModeDialog() {
        val labels = travelModes.map { getString(it.second) }.toTypedArray()
        val checked = travelModes.indexOfFirst { it.first == PTSettings.sortingStrategy }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.travel_mode)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                PTSettings.sortingStrategy = travelModes[which].first
                updateTravelModeValue()
                dialog.dismiss()
            }
            .show()
    }

    // endregion

    private fun initSwitches() {
        fun bind(switch: CompoundButton, value: Boolean, onChanged: (Boolean) -> Unit) {
            switch.isChecked = value
            switch.setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }

        bind(binding.switchBus, PTSettings.useBus) { PTSettings.useBus = it }
        bind(binding.switchUnderground, PTSettings.useUnderground) { PTSettings.useUnderground = it }
        bind(binding.switchRailway, PTSettings.useRailway) { PTSettings.useRailway = it }
        bind(binding.switchTram, PTSettings.useTram) { PTSettings.useTram = it }
        bind(binding.switchFerry, PTSettings.useFerry) { PTSettings.useFerry = it }
        bind(binding.switchOther, PTSettings.useOther) { PTSettings.useOther = it }
        bind(binding.switchWheelchair, PTSettings.wheelchair) { PTSettings.wheelchair = it }
        bind(binding.switchBicycle, PTSettings.bicycle) { PTSettings.bicycle = it }
    }
}
