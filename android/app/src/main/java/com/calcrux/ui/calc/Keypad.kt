package com.calcrux.ui.calc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcrux.R

// ── Key definitions ────────────────────────────────────────────────────────────

sealed class CalcKey {
    data class Digit(val ch: String) : CalcKey()
    data class Op(val ch: String) : CalcKey()
    data object Equals : CalcKey()
    data object Backspace : CalcKey()
    data object ClearAll : CalcKey()
    data class Func(val name: String, val insert: String) : CalcKey()
    /** Toggles inverse trig functions. */
    data object Inv : CalcKey()
    /** Toggles degrees / radians. */
    data object Angle : CalcKey()
}

private val sinKey = CalcKey.Func("sin", "sin(")
private val cosKey = CalcKey.Func("cos", "cos(")
private val tanKey = CalcKey.Func("tan", "tan(")
private val asinKey = CalcKey.Func("sin⁻¹", "arcsin(")
private val acosKey = CalcKey.Func("cos⁻¹", "arccos(")
private val atanKey = CalcKey.Func("tan⁻¹", "arctan(")
private val powKey = CalcKey.Func("xʸ", "^")
private val logKey = CalcKey.Func("log", "log(")
private val lnKey = CalcKey.Func("ln", "ln(")
private val sqrtKey = CalcKey.Func("√", "√(")
private val factKey = CalcKey.Op("!")
private val squareKey = CalcKey.Func("x²", "^2")
private val piKey = CalcKey.Func("π", "π")
private val eKey = CalcKey.Func("e", "e")

/** Inserts that open a function call; backspace removes each as one token (longest first). */
internal val functionInserts: List<String> =
    listOf(sinKey, cosKey, tanKey, asinKey, acosKey, atanKey, logKey, lnKey, sqrtKey)
        .map { it.insert }
        .sortedByDescending { it.length }

// Standard 4-column pad shared by both modes (MIUI layout).
private val basicRows: List<List<CalcKey>> = listOf(
    listOf(CalcKey.ClearAll, CalcKey.Backspace, CalcKey.Op("%"), CalcKey.Op("÷")),
    listOf(CalcKey.Digit("7"), CalcKey.Digit("8"), CalcKey.Digit("9"), CalcKey.Op("×")),
    listOf(CalcKey.Digit("4"), CalcKey.Digit("5"), CalcKey.Digit("6"), CalcKey.Op("-")),
    listOf(CalcKey.Digit("1"), CalcKey.Digit("2"), CalcKey.Digit("3"), CalcKey.Op("+")),
    listOf(CalcKey.Digit("00"), CalcKey.Digit("0"), CalcKey.Op("."), CalcKey.Equals),
)

// Scientific mode: two function rows on top, one function column on the left.
private fun scientificRows(invMode: Boolean): List<List<CalcKey>> {
    val leftColumn = listOf(sqrtKey, factKey, squareKey, piKey, eKey)
    return listOf(
        listOf(CalcKey.Inv, CalcKey.Angle) +
            if (invMode) listOf(asinKey, acosKey, atanKey) else listOf(sinKey, cosKey, tanKey),
        listOf(powKey, logKey, lnKey, CalcKey.Op("("), CalcKey.Op(")")),
    ) + basicRows.mapIndexed { i, row -> listOf(leftColumn[i]) + row }
}

// ── Keypad composable ──────────────────────────────────────────────────────────

@Composable
fun Keypad(
    onKey: (CalcKey) -> Unit,
    invMode: Boolean,
    degreesMode: Boolean,
    scientificMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = if (scientificMode) scientificRows(invMode) else basicRows
    val gap = if (scientificMode) 6.dp else 10.dp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { key ->
                    KeyButton(
                        key = key,
                        active = (key == CalcKey.Inv && invMode),
                        degreesMode = degreesMode,
                        compact = scientificMode,
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ── Key button ─────────────────────────────────────────────────────────────────

private enum class KeyRole { Digit, Operator, Function, Toggle, Equals }

private val KeyShape = RoundedCornerShape(18.dp)

@Composable
private fun KeyButton(
    key: CalcKey,
    active: Boolean,
    degreesMode: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val role = keyRole(key)
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        active -> colors.primaryContainer to colors.onPrimaryContainer
        role == KeyRole.Equals -> colors.primary to colors.onPrimary
        role == KeyRole.Operator -> colors.surfaceContainerHigh to colors.primary
        role == KeyRole.Function || role == KeyRole.Toggle ->
            colors.surfaceContainer to colors.onSurfaceVariant
        else -> colors.surfaceContainerHigh to colors.onSurface
    }
    val description = keyDescription(key)

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = KeyShape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (key == CalcKey.Backspace) {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp),
            )
        } else {
            Text(
                text = keyLabel(key, degreesMode),
                fontSize = keyFontSize(role, compact),
                fontWeight = if (role == KeyRole.Toggle) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

private fun keyRole(key: CalcKey): KeyRole = when (key) {
    is CalcKey.Digit -> KeyRole.Digit
    CalcKey.Equals -> KeyRole.Equals
    CalcKey.Inv, CalcKey.Angle -> KeyRole.Toggle
    is CalcKey.Func -> KeyRole.Function
    is CalcKey.Op -> when (key.ch) {
        "!", "(", ")" -> KeyRole.Function
        "." -> KeyRole.Digit
        else -> KeyRole.Operator
    }
    CalcKey.Backspace, CalcKey.ClearAll -> KeyRole.Operator
}

private fun keyFontSize(role: KeyRole, compact: Boolean): TextUnit = when (role) {
    KeyRole.Function, KeyRole.Toggle -> if (compact) 17.sp else 20.sp
    KeyRole.Equals -> if (compact) 30.sp else 36.sp
    else -> if (compact) 24.sp else 30.sp
}

@Composable
private fun keyLabel(key: CalcKey, degreesMode: Boolean): String = when (key) {
    is CalcKey.Digit -> key.ch
    is CalcKey.Op -> if (key.ch == "-") "−" else key.ch
    is CalcKey.Func -> key.name
    CalcKey.Equals -> "="
    CalcKey.Backspace -> ""
    CalcKey.ClearAll -> "AC"
    CalcKey.Inv -> "INV"
    CalcKey.Angle -> stringResource(if (degreesMode) R.string.calc_degrees else R.string.calc_radians)
}

/** Spoken labels for keys whose glyph TalkBack would read poorly. */
@Composable
private fun keyDescription(key: CalcKey): String? {
    val res = when (key) {
        CalcKey.Backspace -> R.string.key_backspace
        CalcKey.ClearAll -> R.string.key_clear_all
        CalcKey.Inv -> R.string.key_inverse
        sqrtKey -> R.string.key_sqrt
        squareKey -> R.string.key_square
        powKey -> R.string.key_power
        factKey -> R.string.key_factorial
        else -> return null
    }
    return stringResource(res)
}
