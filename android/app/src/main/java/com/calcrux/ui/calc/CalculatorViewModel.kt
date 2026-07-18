package com.calcrux.ui.calc

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcrux.data.HistoryDao
import com.calcrux.data.HistoryEntry
import com.calcrux.data.RustBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class CalculatorState(
    /** Editable / re-evaluable expression (user input, or refeed after `=`). */
    val expression: String = "",
    /** Live preview display string while editing. */
    val preview: String = "",
    /** Pretty result shown only when [isResult] is true (may contain overlines). */
    val resultDisplay: String = "",
    val error: String = "",
    val degreesMode: Boolean = true,
    val invMode: Boolean = false,
    val scientificMode: Boolean = true,
    /** True after pressing =; keeps the display scrolled to the start of the result. */
    val isResult: Boolean = false,
)

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val historyDao: HistoryDao,
) : ViewModel() {
    private val _state = MutableStateFlow(CalculatorState())
    val state = _state.asStateFlow()

    fun toggleAngleMode() {
        _state.update { s ->
            s.copy(
                degreesMode = !s.degreesMode,
                preview = recompute(s.expression, !s.degreesMode),
            )
        }
    }

    fun toggleInvMode() {
        _state.update { it.copy(invMode = !it.invMode) }
    }

    fun toggleScientificMode() {
        _state.update { it.copy(scientificMode = !it.scientificMode) }
    }

    fun setExpression(expr: String) {
        _state.update { s ->
            s.copy(
                expression = expr,
                preview = recompute(expr, s.degreesMode),
                resultDisplay = "",
                error = "",
                isResult = false,
            )
        }
    }

    fun onKey(key: CalcKey) {
        _state.update { s ->
            when (key) {
                CalcKey.Clear, CalcKey.ClearAll ->
                    s.copy(
                        expression = "",
                        preview = "",
                        resultDisplay = "",
                        error = "",
                        isResult = false,
                    )

                CalcKey.Backspace -> {
                    if (s.isResult) {
                        // Never half-edit a refeed atom; clear instead.
                        s.copy(
                            expression = "",
                            preview = "",
                            resultDisplay = "",
                            error = "",
                            isResult = false,
                        )
                    } else {
                        val expr = s.expression.dropLast(1)
                        s.copy(
                            expression = expr,
                            preview = recompute(expr, s.degreesMode),
                            resultDisplay = "",
                            error = "",
                            isResult = false,
                        )
                    }
                }

                CalcKey.Equals -> evaluate(s)

                is CalcKey.Digit -> {
                    if (s.isResult) {
                        // Start a fresh entry after a result.
                        replaceExpression(s, key.ch)
                    } else {
                        append(s, key.ch)
                    }
                }

                is CalcKey.Op -> {
                    when {
                        key.ch in arithmeticOperators -> {
                            // Continue from refeed (already stored in expression after `=`).
                            val expression = normalizeArithmeticOperatorAppend(s.expression, key.ch)
                                ?: return@update appendLeavingResult(s, key.ch)
                            s.copy(
                                expression = expression,
                                preview = recompute(expression, s.degreesMode),
                                resultDisplay = "",
                                error = "",
                                isResult = false,
                            )
                        }
                        // `.` and other non-arithmetic ops: replace after result.
                        s.isResult -> replaceExpression(s, key.ch)
                        else -> append(s, key.ch)
                    }
                }

                is CalcKey.Func -> {
                    if (s.isResult) {
                        replaceExpression(s, key.insert)
                    } else {
                        append(s, key.insert)
                    }
                }
            }
        }
    }

    private fun replaceExpression(s: CalculatorState, text: String): CalculatorState =
        s.copy(
            expression = text,
            preview = recompute(text, s.degreesMode),
            resultDisplay = "",
            error = "",
            isResult = false,
        )

    private fun append(s: CalculatorState, ch: String): CalculatorState {
        val expr = s.expression + ch
        return s.copy(
            expression = expr,
            preview = recompute(expr, s.degreesMode),
            resultDisplay = "",
            error = "",
            isResult = false,
        )
    }

    private fun appendLeavingResult(s: CalculatorState, ch: String): CalculatorState {
        // Fallback path when normalize returns null for a non-arithmetic op.
        return if (s.isResult) replaceExpression(s, ch) else append(s, ch)
    }

    private fun evaluate(s: CalculatorState): CalculatorState {
        if (s.expression.isBlank()) return s
        return try {
            val result = RustBridge.calcEval(s.expression, s.degreesMode)
            viewModelScope.launch {
                historyDao.insert(
                    HistoryEntry(
                        expression = s.expression,
                        result = result.display,
                        source = "calculator",
                    ),
                )
            }
            s.copy(
                expression = result.refeed,
                preview = "",
                resultDisplay = result.display,
                error = "",
                isResult = true,
            )
        } catch (e: Exception) {
            s.copy(error = e.message?.take(80) ?: "Error")
        }
    }

    private fun recompute(expr: String, degrees: Boolean): String {
        if (expr.isBlank()) return ""
        return try {
            RustBridge.calcEval(expr, degrees).display
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private val arithmeticOperators = setOf("+", "-", "×", "÷")

        /**
         * If [ch] is a four-function operator, return the expression after
         * applying "replace last operator" / append rules; otherwise null.
         */
        internal fun normalizeArithmeticOperatorAppend(expression: String, ch: String): String? {
            if (ch !in arithmeticOperators) return null
            if (expression.isBlank()) return expression
            return if (expression.takeLast(1) in arithmeticOperators) {
                expression.dropLast(1) + ch
            } else {
                expression + ch
            }
        }
    }
}
