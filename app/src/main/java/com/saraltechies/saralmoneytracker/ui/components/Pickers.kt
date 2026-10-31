package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.Period
import com.saraltechies.saralmoneytracker.model.PeriodKind
import com.saraltechies.saralmoneytracker.model.Periods
import java.time.LocalDate

private const val DAY_MS = 24L * 60 * 60 * 1000

private val PeriodSaver = Saver<Period, List<Any>>(
    save = { listOf(it.kind.name, it.start.toEpochDay(), it.end.toEpochDay()) },
    restore = { Period(PeriodKind.valueOf(it[0] as String), LocalDate.ofEpochDay(it[1] as Long), LocalDate.ofEpochDay(it[2] as Long)) },
)

/** The period a screen shows; follows changes to the week start and month start settings. */
@Composable
fun rememberPeriod(initial: PeriodKind): MutableState<Period> {
    val prefs = LocalPeriodPrefs.current
    val state = rememberSaveable(stateSaver = PeriodSaver) { mutableStateOf(Periods.containing(LocalDate.now(), initial, prefs)) }
    LaunchedEffect(prefs) {
        val p = state.value
        if (p.kind != PeriodKind.CUSTOM) state.value = Periods.containing(p.start, p.kind, prefs)
    }
    return state
}

/** Day / Week / Month / Year / Custom switch with previous and next arrows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodBar(period: Period, onChange: (Period) -> Unit, modifier: Modifier = Modifier) {
    val prefs = LocalPeriodPrefs.current
    var pickRange by remember { mutableStateOf(false) }
    val kinds = PeriodKind.entries
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { i, kind ->
                SegmentedButton(
                    selected = period.kind == kind,
                    onClick = {
                        if (kind == PeriodKind.CUSTOM) pickRange = true
                        else onChange(Periods.containing(if (LocalDate.now() in period) LocalDate.now() else period.start, kind, prefs))
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                    icon = {},
                ) { Text(stringResource(periodKindLabel(kind)), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(Periods.shift(period, -1, prefs)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_period))
            }
            TextButton(onClick = { if (period.kind == PeriodKind.CUSTOM) pickRange = true else onChange(Periods.containing(LocalDate.now(), period.kind, prefs)) }, modifier = Modifier.weight(1f)) {
                Text(Dates.period(period), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
            }
            IconButton(onClick = { onChange(Periods.shift(period, 1, prefs)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_period))
            }
        }
    }
    if (pickRange) {
        DateRangeDialog(
            initial = period,
            onDismiss = { pickRange = false },
            onPick = { start, end -> onChange(Periods.custom(start, end)) },
        )
    }
}

fun periodKindLabel(kind: PeriodKind): Int = when (kind) {
    PeriodKind.DAY -> R.string.period_day
    PeriodKind.WEEK -> R.string.period_week
    PeriodKind.MONTH -> R.string.period_month
    PeriodKind.YEAR -> R.string.period_year
    PeriodKind.CUSTOM -> R.string.period_custom
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(initial: Period, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.toEpochDay() * DAY_MS,
        initialSelectedEndDateMillis = initial.end.toEpochDay() * DAY_MS,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis
                    if (start != null) {
                        val end = state.selectedEndDateMillis ?: start
                        onPick(LocalDate.ofEpochDay(Math.floorDiv(start, DAY_MS)), LocalDate.ofEpochDay(Math.floorDiv(end, DAY_MS)))
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DateRangePicker(state, modifier = Modifier.weight(1f)) }
}
