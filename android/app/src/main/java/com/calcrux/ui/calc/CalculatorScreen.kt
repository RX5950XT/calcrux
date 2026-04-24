package com.calcrux.ui.calc

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import android.content.ClipData
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.R

@Composable
fun CalculatorScreen(
    vm: CalculatorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val exprScroll = rememberScrollState()

    // Auto-scroll to end as the expression grows.
    LaunchedEffect(state.expression) { exprScroll.scrollTo(exprScroll.maxValue) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { vm.onKey(CalcKey.Equals); true }
                    Key.Backspace -> { vm.onKey(CalcKey.Backspace); true }
                    Key.Escape -> { vm.onKey(CalcKey.Clear); true }
                    Key.Zero, Key.NumPad0 -> { vm.onKey(CalcKey.Digit("0")); true }
                    Key.One, Key.NumPad1 -> { vm.onKey(CalcKey.Digit("1")); true }
                    Key.Two, Key.NumPad2 -> { vm.onKey(CalcKey.Digit("2")); true }
                    Key.Three, Key.NumPad3 -> { vm.onKey(CalcKey.Digit("3")); true }
                    Key.Four, Key.NumPad4 -> { vm.onKey(CalcKey.Digit("4")); true }
                    Key.Five, Key.NumPad5 -> { vm.onKey(CalcKey.Digit("5")); true }
                    Key.Six, Key.NumPad6 -> { vm.onKey(CalcKey.Digit("6")); true }
                    Key.Seven, Key.NumPad7 -> { vm.onKey(CalcKey.Digit("7")); true }
                    Key.Eight, Key.NumPad8 -> { vm.onKey(CalcKey.Digit("8")); true }
                    Key.Nine, Key.NumPad9 -> { vm.onKey(CalcKey.Digit("9")); true }
                    Key.Plus, Key.NumPadAdd -> { vm.onKey(CalcKey.Op("+")); true }
                    Key.Minus, Key.NumPadSubtract -> { vm.onKey(CalcKey.Op("-")); true }
                    Key.NumPadMultiply -> { vm.onKey(CalcKey.Op("×")); true }
                    Key.NumPadDivide -> { vm.onKey(CalcKey.Op("÷")); true }
                    Key.Period, Key.NumPadDot -> { vm.onKey(CalcKey.Op(".")); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Expression / result display ────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            // Copy button row
            if (state.expression.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = {
                            val text = if (state.preview.isNotEmpty()) state.preview else state.expression
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("result", text)))
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy_result),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Text(
                text = state.expression.ifEmpty { "0" },
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(exprScroll),
                maxLines = 3,
            )
            Spacer(Modifier.height(4.dp))
            if (state.preview.isNotEmpty() && state.preview != state.expression) {
                Text(
                    text = state.preview,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                )
            }
            if (state.error.isNotEmpty()) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        }

        // ── Control row: Sci/Basic + INV + Angle mode (single row) ────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.scientificMode,
                onClick = { vm.toggleScientificMode() },
                label = {
                    Text(
                        if (state.scientificMode) stringResource(R.string.mode_scientific)
                        else stringResource(R.string.mode_basic),
                        fontSize = 12.sp,
                    )
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.scientificMode) {
                    FilterChip(
                        selected = state.invMode,
                        onClick = { vm.toggleInvMode() },
                        label = { Text("INV", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                FilterChip(
                    selected = state.degreesMode,
                    onClick = { vm.toggleAngleMode() },
                    label = {
                        Text(
                            if (state.degreesMode) stringResource(R.string.calc_degrees)
                            else stringResource(R.string.calc_radians),
                            fontSize = 12.sp,
                        )
                    },
                )
            }
        }

        // ── Keypad ─────────────────────────────────────────────────────────
        Keypad(
            onKey = { key -> vm.onKey(key) },
            invMode = state.invMode,
            scientificMode = state.scientificMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
    }
}
