package com.calcrux.ui.calc

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Key definitions ────────────────────────────────────────────────────────────

sealed class CalcKey {
    data class Digit(val ch: String) : CalcKey()
    data class Op(val ch: String) : CalcKey()
    data object Equals : CalcKey()
    data object Backspace : CalcKey()
    data object Clear : CalcKey()
    data object ClearAll : CalcKey()
    data class Func(val name: String, val insert: String) : CalcKey()
}

// Rows that are independent of INV mode
private val staticRows: List<List<CalcKey>> = listOf(
    // Row 1: more scientific
    listOf(
        CalcKey.Func("√",  "√("),
        CalcKey.Func("x²", "^2"),
        CalcKey.Func("xⁿ", "^"),
        CalcKey.Func("π",  "π"),
        CalcKey.Func("e",  "e"),
    ),
    // Row 2: brackets / special
    listOf(
        CalcKey.Op("("),
        CalcKey.Op(")"),
        CalcKey.Op("%"),
        CalcKey.Op("!"),
        CalcKey.ClearAll,
    ),
    // Row 3
    listOf(
        CalcKey.Digit("7"),
        CalcKey.Digit("8"),
        CalcKey.Digit("9"),
        CalcKey.Op("÷"),
        CalcKey.Backspace,
    ),
    // Row 4
    listOf(
        CalcKey.Digit("4"),
        CalcKey.Digit("5"),
        CalcKey.Digit("6"),
        CalcKey.Op("×"),
        CalcKey.Op("+"),
    ),
    // Row 5
    listOf(
        CalcKey.Digit("1"),
        CalcKey.Digit("2"),
        CalcKey.Digit("3"),
        CalcKey.Op("-"),
        CalcKey.Equals,
    ),
    // Row 6
    listOf(
        CalcKey.Digit("0"),
        CalcKey.Digit("00"),
        CalcKey.Op("."),
        CalcKey.Clear,
    ),
)

private val normalTrigRow = listOf(
    CalcKey.Func("sin",  "sin("),
    CalcKey.Func("cos",  "cos("),
    CalcKey.Func("tan",  "tan("),
    CalcKey.Func("ln",   "ln("),
    CalcKey.Func("log",  "log("),
)

private val inverseTrigRow = listOf(
    CalcKey.Func("arcsin", "arcsin("),
    CalcKey.Func("arccos", "arccos("),
    CalcKey.Func("arctan", "arctan("),
    CalcKey.Func("ln",     "ln("),
    CalcKey.Func("log",    "log("),
)

// ── Keypad composable ──────────────────────────────────────────────────────────

@Composable
fun Keypad(
    onKey: (CalcKey) -> Unit,
    invMode: Boolean,
    scientificMode: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val trigRow = if (invMode) inverseTrigRow else normalTrigRow
    val sciRows = if (scientificMode) listOf(trigRow, staticRows[0]) else emptyList()
    val allRows = sciRows + staticRows.drop(1)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        allRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── Key button ─────────────────────────────────────────────────────────────────

@Composable
private fun KeyButton(
    key: CalcKey,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, containerColor, contentColor) = keyStyle(key)

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(text = label, fontSize = 15.sp)
    }
}

// ── Key styling ────────────────────────────────────────────────────────────────

@Composable
private fun keyStyle(key: CalcKey): Triple<String, Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (key) {
        // Digits: white/surface background, dark text
        is CalcKey.Digit   -> Triple(key.ch,   colors.surface,          colors.onSurface)
        // Operators: white background, orange (primary) text
        is CalcKey.Op      -> Triple(key.ch,   colors.surface,          colors.primary)
        // Scientific functions: secondary container, primary text
        is CalcKey.Func    -> Triple(key.name, colors.secondaryContainer, colors.primary)
        // Equals: orange filled (signature Mi Calculator button)
        CalcKey.Equals     -> Triple("=",      colors.primary,           colors.onPrimary)
        // Backspace: surface, orange text
        CalcKey.Backspace  -> Triple("⌫",      colors.surface,           colors.primary)
        // C: surface, error color
        CalcKey.Clear      -> Triple("C",      colors.surface,           colors.error)
        // AC: error container
        CalcKey.ClearAll   -> Triple("AC",     colors.errorContainer,    colors.onErrorContainer)
    }
}
