package com.calcrux.ui.fx

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.ui.shared.NumPad
import com.calcrux.ui.shared.TabularNumbers
import com.calcrux.ui.shared.prettyMath

/** Which row the currency picker edits; [ADD_ROW] appends a new one. */
private const val ADD_ROW = -1

@Composable
fun FxScreen(
    visible: Boolean = true,
    vm: FxViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var pickerRow by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    // Long-press drag: the row being moved and how far it is from its slot.
    var draggingCode by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(visible) { if (visible) vm.refreshIfStale() }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status bar ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val statusColor = when (state.status) {
                FxStatus.Loading -> MaterialTheme.colorScheme.outline
                FxStatus.Ok      -> MaterialTheme.colorScheme.primary
                FxStatus.Offline -> MaterialTheme.colorScheme.error
            }
            Column {
                Text(
                    text = when (state.status) {
                        FxStatus.Loading -> "更新中…"
                        FxStatus.Ok      -> "匯率更新：${state.lastUpdated}"
                        FxStatus.Offline -> "離線 — ${state.lastUpdated}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
                // Attribution required by the free ExchangeRate-API terms.
                Text(
                    text = "Rates By Exchange Rate API・每日更新・中間價",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

        // ── Currency rows: tap a row to type in it ─────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.rows, key = { _, code -> code }) { i, code ->
                val dragging = code == draggingCode
                FxRow(
                    modifier = Modifier
                        .then(
                            if (dragging) {
                                Modifier.zIndex(1f).graphicsLayer {
                                    translationY = dragOffset
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 12f
                                }
                            } else {
                                Modifier.animateItem()
                            },
                        )
                        .pointerInput(code) {
                            // Wait for the list to re-lay out after a move before testing again.
                            var awaitingIndex = -1
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingCode = code
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val items = listState.layoutInfo.visibleItemsInfo
                                    val self = items.firstOrNull { it.key == code } ?: return@detectDragGesturesAfterLongPress
                                    if (awaitingIndex >= 0 && self.index != awaitingIndex) return@detectDragGesturesAfterLongPress
                                    awaitingIndex = -1
                                    val center = self.offset + dragOffset + self.size / 2f
                                    val target = items.firstOrNull {
                                        it.key != code && it.key is String &&
                                            center > it.offset && center < it.offset + it.size
                                    }
                                    if (target != null) {
                                        // The row re-lays out at the target slot; keep it under the finger.
                                        dragOffset -= target.offset - self.offset
                                        vm.moveRow(self.index, target.index)
                                        awaitingIndex = target.index
                                    }
                                },
                                onDragEnd = { draggingCode = null },
                                onDragCancel = { draggingCode = null },
                            )
                        },
                    code = code,
                    value = state.values.getOrElse(i) { "" },
                    isActive = i == state.activeIndex,
                    onClick = { vm.setActive(i) },
                    onCurrencyClick = { pickerRow = i },
                )
            }
            item {
                TextButton(
                    onClick = { pickerRow = ADD_ROW },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新增幣別")
                }
            }
        }

        // ── NumPad ─────────────────────────────────────────────────────────
        NumPad(
            onDigit = vm::onNumKey,
            onDecimal = vm::onDecimalKey,
            onBackspace = vm::onBackspaceKey,
            onClear = vm::onClear,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.85f),
        )
    }

    // ── Currency picker dialog ─────────────────────────────────────────────
    pickerRow?.let { row ->
        val adding = row == ADD_ROW
        CurrencyPickerDialog(
            title = if (adding) "新增幣別" else "選擇幣別",
            codes = if (adding) state.codes.filterNot { it in state.rows } else state.codes,
            selected = state.rows.getOrNull(row),
            pinnedCodes = state.pinnedCodes,
            onSelect = { code ->
                if (adding) vm.addRow(code) else vm.selectCode(row, code)
                pickerRow = null
            },
            onTogglePin = vm::togglePin,
            onRemove = if (!adding && state.rows.size > 1) {
                { vm.removeRow(row); pickerRow = null }
            } else null,
            onDismiss = { pickerRow = null },
        )
    }
}

// ── Currency row ───────────────────────────────────────────────────────────────

@Composable
private fun FxRow(
    modifier: Modifier,
    code: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onCurrencyClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val displayText = if (value.isEmpty()) "0" else prettyMath(value)
    val textColor = when {
        value.isEmpty() -> colors.onSurface.copy(alpha = 0.3f)
        isActive -> colors.primary
        else -> colors.onSurface
    }
    val valueScroll = rememberScrollState()
    LaunchedEffect(displayText, isActive) {
        if (isActive) valueScroll.scrollTo(valueScroll.maxValue) else valueScroll.scrollTo(0)
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        border = if (isActive) BorderStroke(2.dp, colors.primary) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCurrencyClick,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(currencyFlag(code), fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = code,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = currencyName(code),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }

            Text(
                text = displayText,
                fontSize = 28.sp,
                style = TabularNumbers,
                textAlign = TextAlign.End,
                color = textColor,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(valueScroll),
            )
        }
    }
}

// ── Currency picker dialog ─────────────────────────────────────────────────────

@Composable
private fun CurrencyPickerDialog(
    title: String,
    codes: List<String>,
    selected: String?,
    pinnedCodes: Set<String>,
    onSelect: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onRemove: (() -> Unit)?,
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
        title = { Text(title) },
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
                            Text(currencyFlag(code), fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
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
                            IconButton(onClick = { onTogglePin(code) }) {
                                Icon(
                                    imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (isPinned) "取消釘選" else "釘選",
                                    tint = if (isPinned)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
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
        dismissButton = onRemove?.let { remove ->
            {
                TextButton(onClick = remove) {
                    Text("移除此欄", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
