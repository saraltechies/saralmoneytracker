package com.saraltechies.saralmoneytracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saraltechies.saralmoneytracker.R
import com.saraltechies.saralmoneytracker.model.AmountExpression

/** Rules for typing on the amount keypad, kept apart from the UI so they're easy to follow. */
object KeypadInput {
    private const val MAX_LENGTH = 32

    fun press(current: String, key: Char): String {
        if (current.length >= MAX_LENGTH) return current
        val last = current.lastOrNull()
        return when {
            key in AmountExpression.OPERATORS -> when {
                current.isEmpty() -> current
                last != null && last in AmountExpression.OPERATORS -> current.dropLast(1) + key
                last == '.' -> current.dropLast(1) + key
                else -> current + key
            }
            key == '.' -> {
                val number = lastNumber(current)
                when {
                    number.contains('.') -> current
                    number.isEmpty() -> current + "0."
                    else -> current + key
                }
            }
            else -> {
                val number = lastNumber(current)
                when {
                    // Two decimals at most.
                    number.substringAfter('.', "").length >= 2 && number.contains('.') -> current
                    number == "0" -> current.dropLast(1) + key
                    number.replace(".", "").length >= 10 && !number.contains('.') -> current
                    else -> current + key
                }
            }
        }
    }

    fun backspace(current: String): String = current.dropLast(1)

    private fun lastNumber(s: String): String = s.takeLastWhile { it.isDigit() || it == '.' }
}

@Composable
fun Keypad(onKey: (Char) -> Unit, onBackspace: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val rows = listOf(
        listOf('7', '8', '9', '÷'),
        listOf('4', '5', '6', '×'),
        listOf('1', '2', '3', '-'),
        listOf('.', '0', '⌫', '+'),
    )
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (key in row) {
                    val isOp = key in AmountExpression.OPERATORS
                    val backspace = stringResource(R.string.backspace)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (key == '⌫') onBackspace() else onKey(key)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isOp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).height(52.dp).semantics { if (key == '⌫') contentDescription = backspace },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == '⌫') {
                                Icon(Icons.AutoMirrored.Filled.Backspace, null)
                            } else {
                                Text(
                                    if (key == '-') "−" else key.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = if (isOp) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
