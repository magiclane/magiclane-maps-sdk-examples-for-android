/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.definepersistentroadblockcompose.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magiclane.sdk.compose.components.R as ComponentsR
import com.magiclane.sdk.examples.definepersistentroadblockcompose.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// Default roadblock validity, like Magic Earth: from now until one hour later.
private const val DEFAULT_ROADBLOCK_DURATION_MS = 60L * 60 * 1000

/**
 * The set-roadblock panel, copied from the Magic Earth set roadblock view: roadblock
 * name, From/To toggle selecting which end of the validity interval the date and time
 * buttons edit, and "Done" defining the roadblock. [onClose] cancels the definition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetRoadblockPanel(
    defaultName: String,
    onClose: () -> Unit,
    onDone: (name: String, startMillis: Long, endMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(defaultName) { mutableStateOf(defaultName) }
    var fromMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var toMillis by remember {
        mutableLongStateOf(System.currentTimeMillis() + DEFAULT_ROADBLOCK_DURATION_MS)
    }

    // Which end of the validity interval the date and time buttons currently edit.
    var isEditingFrom by remember { mutableStateOf(true) }
    val editedMillis = if (isEditingFrom) fromMillis else toMillis
    val setEdited = { millis: Long ->
        if (isEditingFrom) fromMillis = millis else toMillis = millis
    }

    var datePickerVisible by remember { mutableStateOf(false) }
    var timePickerVisible by remember { mutableStateOf(false) }

    val submit = {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            onDone(trimmed, fromMillis, toMillis)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(ComponentsR.drawable.ml_close),
                        contentDescription = stringResource(R.string.close_set_roadblock),
                    )
                }
                Text(
                    text = stringResource(R.string.define_roadblock),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = submit, enabled = name.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.done).uppercase(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.roadblock_name)) },
                singleLine = true,
                trailingIcon = {
                    if (name.isNotEmpty()) {
                        IconButton(onClick = { name = "" }) {
                            Icon(
                                painter = painterResource(ComponentsR.drawable.ml_close),
                                contentDescription = stringResource(R.string.clear_roadblock_name),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            // Selects which end of the validity interval the date and time buttons edit.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                SegmentedButton(
                    selected = isEditingFrom,
                    onClick = { isEditingFrom = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.from_label), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                SegmentedButton(
                    selected = !isEditingFrom,
                    onClick = { isEditingFrom = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.to_label), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Row(modifier = Modifier.padding(top = 16.dp)) {
                OutlinedButton(
                    onClick = { datePickerVisible = true },
                    modifier = Modifier.weight(2f),
                ) {
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(editedMillis)),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = { timePickerVisible = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(editedMillis)),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    if (datePickerVisible) {
        // The Material date picker works in UTC day timestamps: convert back and forth.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDayAsUtcMillis(editedMillis),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { setEdited(withUtcDate(editedMillis, it)) }
                    datePickerVisible = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (timePickerVisible) {
        val calendar = Calendar.getInstance().apply { timeInMillis = editedMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar[Calendar.HOUR_OF_DAY],
            initialMinute = calendar[Calendar.MINUTE],
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { timePickerVisible = false },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    setEdited(withTime(editedMillis, timePickerState.hour, timePickerState.minute))
                    timePickerVisible = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { timePickerVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** The local calendar day of [millis] as a UTC day timestamp (what DatePicker expects). */
private fun localDayAsUtcMillis(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local[Calendar.YEAR], local[Calendar.MONTH], local[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

/** [millis] with its local calendar day replaced by the picked UTC day [selectionUtcMillis]. */
private fun withUtcDate(millis: Long, selectionUtcMillis: Long): Long {
    val selected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = selectionUtcMillis
    }
    return Calendar.getInstance().apply {
        timeInMillis = millis
        set(selected[Calendar.YEAR], selected[Calendar.MONTH], selected[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

/** [millis] with its local wall-clock time replaced by [hour]:[minute]. */
private fun withTime(millis: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
}.timeInMillis
