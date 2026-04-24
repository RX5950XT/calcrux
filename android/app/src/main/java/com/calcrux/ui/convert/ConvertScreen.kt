package com.calcrux.ui.convert

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.ui.shared.NumPad

@Composable
fun ConvertScreen(
    vm: ConvertViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Category chips (8 items — plain Row is lighter than LazyRow) ─────
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.categories.forEach { cat ->
                val selected = cat == state.category
                FilterChip(
                    selected = selected,
                    onClick = { vm.selectCategory(cat) },
                    label = { Text(categoryLabel(cat)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        HorizontalDivider()

        Spacer(Modifier.height(12.dp))

        // ── From row ───────────────────────────────────────────────────────
        ConvertInputCard(
            unitLabel = unitShortLabel(state.fromUnit),
            unitKey = state.fromUnit,
            value = state.fromText,
            isInput = true,
            onUnitClick = { showFromPicker = true },
        )

        // ── Swap button ────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = vm::swap,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "互換",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── To row ─────────────────────────────────────────────────────────
        ConvertInputCard(
            unitLabel = unitShortLabel(state.toUnit),
            unitKey = state.toUnit,
            value = state.toText,
            isInput = false,
            onUnitClick = { showToPicker = true },
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // ── Error ──────────────────────────────────────────────────────────
        if (state.error.isNotEmpty()) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.weight(1f))   // push numpad to bottom
        HorizontalDivider()

        // ── NumPad ─────────────────────────────────────────────────────────
        NumPad(
            onDigit = vm::onNumKey,
            onDecimal = vm::onDecimalKey,
            onBackspace = vm::onBackspaceKey,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
    }

    // ── Unit picker dialogs ────────────────────────────────────────────────
    if (showFromPicker) {
        UnitPickerDialog(
            units = state.units,
            selected = state.fromUnit,
            onSelect = { vm.selectFrom(it); showFromPicker = false },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        UnitPickerDialog(
            units = state.units,
            selected = state.toUnit,
            onSelect = { vm.selectTo(it); showToPicker = false },
            onDismiss = { showToPicker = false },
        )
    }
}

// ── Single conversion row (from / to) ─────────────────────────────────────────

@Composable
private fun ConvertInputCard(
    unitLabel: String,
    unitKey: String,
    value: String,
    isInput: Boolean,
    onUnitClick: () -> Unit,
) {
    val displayText = when {
        isInput && value.isEmpty() -> "0"
        !isInput && value.isEmpty() -> "—"
        else -> value
    }
    val textColor = when {
        isInput && value.isEmpty() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isInput -> MaterialTheme.colorScheme.onSurface
        value.isEmpty() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Unit selector button
            TextButton(
                onClick = onUnitClick,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = unitLabel,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = unitKey,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Value display
            Text(
                text = displayText,
                fontSize = 32.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Unit picker dialog ─────────────────────────────────────────────────────────

@Composable
private fun UnitPickerDialog(
    units: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇單位") },
        text = {
            LazyColumn {
                items(units) { unit ->
                    val isSelected = unit == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(unit) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect(unit) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Text(
                            text = unitLabel(unit),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
