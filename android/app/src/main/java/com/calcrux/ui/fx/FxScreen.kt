package com.calcrux.ui.fx

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
fun FxScreen(
    vm: FxViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status bar ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val statusColor = when (state.status) {
                FxStatus.Loading -> MaterialTheme.colorScheme.outline
                FxStatus.Ok      -> MaterialTheme.colorScheme.primary
                FxStatus.Offline -> MaterialTheme.colorScheme.error
            }
            Text(
                text = when (state.status) {
                    FxStatus.Loading -> "更新中…"
                    FxStatus.Ok      -> "匯率日期：${state.lastUpdated}"
                    FxStatus.Offline -> "離線 — ${state.lastUpdated}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
            IconButton(
                onClick = vm::refresh,
                enabled = state.status != FxStatus.Loading,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "更新匯率",
                    tint = if (state.status != FxStatus.Loading)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline,
                )
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── From row ───────────────────────────────────────────────────────
        FxInputCard(
            code = state.fromCode,
            value = state.fromText,
            isInput = true,
            onCurrencyClick = { showFromPicker = true },
        )

        // ── Swap ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = vm::swap,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(40.dp),
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = "互換",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── To row ─────────────────────────────────────────────────────────
        FxInputCard(
            code = state.toCode,
            value = state.toText,
            isInput = false,
            onCurrencyClick = { showToPicker = true },
        )

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

    // ── Currency picker dialogs ────────────────────────────────────────────
    if (showFromPicker) {
        CurrencyPickerDialog(
            codes = state.codes,
            selected = state.fromCode,
            pinnedCodes = state.pinnedCodes,
            onSelect = { vm.selectFrom(it); showFromPicker = false },
            onTogglePin = vm::togglePin,
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        CurrencyPickerDialog(
            codes = state.codes,
            selected = state.toCode,
            pinnedCodes = state.pinnedCodes,
            onSelect = { vm.selectTo(it); showToPicker = false },
            onTogglePin = vm::togglePin,
            onDismiss = { showToPicker = false },
        )
    }
}

// ── Input card ─────────────────────────────────────────────────────────────────

@Composable
private fun FxInputCard(
    code: String,
    value: String,
    isInput: Boolean,
    onCurrencyClick: () -> Unit,
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
            TextButton(
                onClick = onCurrencyClick,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = code,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = currencyName(code),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
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

// ── Currency picker dialog ─────────────────────────────────────────────────────

@Composable
private fun CurrencyPickerDialog(
    codes: List<String>,
    selected: String,
    pinnedCodes: Set<String>,
    onSelect: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // Preserve pinned-first ordering in search results
    val filtered = remember(codes, query) {
        if (query.isBlank()) codes
        else codes.filter { code ->
            code.contains(query, ignoreCase = true) ||
                currencyName(code).contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇幣別") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜尋幣別…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                LazyColumn {
                    items(filtered) { code ->
                        val isSelected = code == selected
                        val isPinned = code in pinnedCodes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(code) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelect(code) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = currencyName(code),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Pin / unpin star
                            IconButton(
                                onClick = { onTogglePin(code) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = if (isPinned) "取消釘選" else "釘選",
                                    tint = if (isPinned)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉") }
        },
    )
}
