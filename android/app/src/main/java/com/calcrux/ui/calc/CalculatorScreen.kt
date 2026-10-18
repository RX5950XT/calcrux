package com.calcrux.ui.calc

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Velocity
import com.calcrux.ui.history.HistoryScreen
import kotlinx.coroutines.Job
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.R
import com.calcrux.ui.shared.TabularNumbers
import com.calcrux.ui.shared.prettyMath
import kotlinx.coroutines.launch

@Composable
fun CalculatorScreen(
    vm: CalculatorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val exprScroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    // History panel pulled down over the calculator: 0 = closed, 1 = full height.
    var historyProgress by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val settleHistory = { target: Float ->
        settleJob?.cancel()
        settleJob = coroutineScope.launch {
            animate(historyProgress, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                historyProgress = v
            }
        }
    }
    val onHistoryDragStopped = { velocity: Float ->
        settleHistory(
            when {
                velocity > 1500f -> 1f
                velocity < -1500f -> 0f
                historyProgress > 0.4f -> 1f
                else -> 0f
            },
        )
    }
    // Composition only needs open/closed; per-frame progress is read in draw/layout lambdas.
    val historyVisible by remember { derivedStateOf { historyProgress > 0f } }
    BackHandler(enabled = historyVisible) { settleHistory(0f) }

    val mainText = prettyMath(
        when {
            state.isResult && state.resultDisplay.isNotEmpty() -> state.resultDisplay
            state.expression.isNotEmpty() -> state.expression
            else -> "0"
        },
    )

    // Results stay at the start; expressions scroll to the end while typing.
    LaunchedEffect(mainText, state.isResult) {
        if (state.isResult) {
            exprScroll.scrollTo(0)
        } else {
            exprScroll.scrollTo(exprScroll.maxValue)
        }
    }
    // Hardware-keyboard events only reach a focused node.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Clip so the closed history panel never draws over the tab row above.
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val fullHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val dragHistory = { delta: Float ->
            settleJob?.cancel()
            historyProgress = (historyProgress + delta / fullHeightPx).coerceIn(0f, 1f)
        }
        val historyDrag = rememberDraggableState { dragHistory(it) }
        // Newest entry sits at the bottom: pulling up past it closes the panel,
        // pulling down while partly closed re-opens it before the list scrolls.
        val historyScroll = remember(fullHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f || historyProgress >= 1f) {
                        return Offset.Zero
                    }
                    val before = historyProgress
                    dragHistory(available.y)
                    return Offset(0f, (historyProgress - before) * fullHeightPx)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                    dragHistory(available.y)
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (historyProgress >= 1f) return Velocity.Zero
                    onHistoryDragStopped(available.y)
                    return available
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { vm.onKey(CalcKey.Equals); true }
                        Key.Backspace -> { vm.onKey(CalcKey.Backspace); true }
                        Key.Escape -> { vm.onKey(CalcKey.ClearAll); true }
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
                }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            // ── Expression / result display ────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
                    .draggable(
                        historyDrag,
                        Orientation.Vertical,
                        onDragStopped = { onHistoryDragStopped(it) },
                    ),
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = vm::toggleScientificMode) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (state.scientificMode) R.string.mode_scientific else R.string.mode_basic),
                        )
                    }
                    Row {
                        IconButton(onClick = { settleHistory(1f) }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = stringResource(R.string.tab_history),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Copy what the user sees: the result, else the live preview.
                        if (state.expression.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val text = when {
                                        state.isResult && state.resultDisplay.isNotEmpty() -> state.resultDisplay
                                        state.preview.isNotEmpty() -> state.preview
                                        else -> state.expression
                                    }
                                    coroutineScope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("result", text)))
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_result),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (state.isResult && state.evaluatedExpression.isNotEmpty()) {
                    Text(
                        text = prettyMath(state.evaluatedExpression) + " =",
                        style = TabularNumbers,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = mainText,
                    style = TabularNumbers,
                    fontSize = displayFontSize(mainText),
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(exprScroll),
                )
                val secondary = when {
                    state.error.isNotEmpty() -> errorText(state.error)
                    !state.isResult && state.preview != state.expression -> prettyMath(state.preview)
                    else -> ""
                }
                Text(
                    text = secondary,
                    style = TabularNumbers,
                    fontSize = 26.sp,
                    color = if (state.error.isNotEmpty()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Keypad ─────────────────────────────────────────────────────────
            Keypad(
                onKey = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.onKey(key)
                },
                invMode = state.invMode,
                degreesMode = state.degreesMode,
                scientificMode = state.scientificMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (state.scientificMode) 1.9f else 1.5f)
                    .padding(bottom = 12.dp),
            )
        }

        // ── History panel (pull down on the display to open) ──────────────────
        // Always composed (no empty flash on open); slid off-screen and hidden from TalkBack when closed.
        // Moving it with graphicsLayer avoids re-measuring the list every animation frame.
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -(1f - historyProgress) * size.height
                    shadowElevation = if (historyProgress > 0f) 6.dp.toPx() else 0f
                    alpha = if (historyProgress > 0f) 1f else 0f
                }
                .then(
                    if (historyVisible) Modifier.nestedScroll(historyScroll)
                    else Modifier.clearAndSetSemantics {},
                ),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                HistoryScreen(
                    modifier = Modifier.weight(1f),
                    onRestoreExpression = { expr ->
                        vm.setExpression(expr)
                        settleHistory(0f)
                    },
                )
                // Drag handle: pull up (or tap) to close.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .draggable(historyDrag, Orientation.Vertical, onDragStopped = { onHistoryDragStopped(it) })
                        .clickable { settleHistory(0f) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }
        }
    }
}

/** Shrink long numbers so the common case fits without scrolling. */
private fun displayFontSize(text: String): TextUnit = when (text.count { it != '̅' }) {
    in 0..8 -> 64.sp
    in 9..11 -> 52.sp
    in 12..14 -> 42.sp
    else -> 34.sp
}

@Composable
private fun errorText(raw: String): String = stringResource(
    when {
        raw.startsWith("division by zero") -> R.string.error_div_zero
        raw.startsWith("overflow") -> R.string.error_overflow
        raw.startsWith("domain error") -> R.string.error_domain
        else -> R.string.error_syntax
    },
)
