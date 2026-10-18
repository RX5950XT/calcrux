package com.calcrux.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcrux.R

// ── Input rules shared by the unit and currency converters ───────────────────

/** Typed characters allowed, not counting a leading minus. */
private const val MAX_INPUT_LEN = 18

/** Append `0`–`9` or `00`; a lone leading zero is replaced, never repeated. */
fun appendDigit(cur: String, digit: String): String {
    val sign = if (cur.startsWith("-")) "-" else ""
    val body = cur.removePrefix("-")
    if (body.length + digit.length > MAX_INPUT_LEN) return cur
    val newBody = if (body.isEmpty() || body == "0") digit.trimStart('0').ifEmpty { "0" } else body + digit
    return sign + newBody
}

fun appendDecimal(cur: String): String = when {
    '.' in cur -> cur
    cur.removePrefix("-").isEmpty() -> cur + "0."
    else -> "$cur."
}

fun toggleSign(cur: String): String = if (cur.startsWith("-")) cur.drop(1) else "-$cur"

// ── NumPad ───────────────────────────────────────────────────────────────────

private val DIGIT_ROWS = listOf(
    listOf("7", "8", "9"),
    listOf("4", "5", "6"),
    listOf("1", "2", "3"),
)

private val PadShape = RoundedCornerShape(18.dp)

/**
 * 3×4 digit grid plus a tall ⌫ / AC column. The bottom-left key is `±` when
 * [onToggleSign] is given (e.g. temperatures), otherwise `00`.
 */
@Composable
fun NumPad(
    onDigit: (String) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleSign: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val tap = { action: () -> Unit ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }
    val bottomRow = listOf(if (onToggleSign != null) "±" else "00", "0", ".")

    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(3f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (DIGIT_ROWS + listOf(bottomRow)).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        PadKey(
                            onClick = {
                                tap {
                                    when (key) {
                                        "." -> onDecimal()
                                        "±" -> onToggleSign?.invoke()
                                        else -> onDigit(key)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(key, fontSize = 26.sp, style = TabularNumbers)
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PadKey(onClick = { tap(onBackspace) }, modifier = Modifier.weight(1f), accent = true) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.key_backspace),
                    modifier = Modifier.size(24.dp),
                )
            }
            PadKey(onClick = { tap(onClear) }, modifier = Modifier.weight(1f), accent = true) {
                val clearAll = stringResource(R.string.key_clear_all)
                Text("AC", fontSize = 22.sp, modifier = Modifier.semantics { contentDescription = clearAll })
            }
        }
    }
}

@Composable
private fun PadKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxSize().heightIn(min = 48.dp),
        shape = PadShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = if (accent) colors.primary else colors.onSurface,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        content = content,
    )
}
