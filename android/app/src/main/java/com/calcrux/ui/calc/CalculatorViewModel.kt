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
    /** The expression that produced [resultDisplay], shown above it. */
    val evaluatedExpression: String = "",
    /** Raw engine error message (mapped to user-facing text by the screen). */
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

    fun toggleScientificMode() {
        _state.update { it.copy(scientificMode = !it.scientificMode) }
    }

    fun setExpression(expr: String) {
        _state.update { s -> edited(s, expr) }
    }

    fun onKey(key: CalcKey) {
        _state.update { s ->
            when (key) {
                CalcKey.ClearAll -> edited(s, "")

                // Never half-edit a refeed atom like `(1/3)`; clear instead.
                CalcKey.Backspace -> edited(s, if (s.isResult) "" else deleteLast(s.expression))

                // `=` on a shown result is a no-op (avoids duplicate history rows).
                CalcKey.Equals -> if (s.isResult) s else evaluate(s)

                CalcKey.Inv -> s.copy(invMode = !s.invMode)

                CalcKey.Angle -> s.copy(
                    degreesMode = !s.degreesMode,
                    preview = recompute(s.expression, !s.degreesMode),
                )

                // Start a fresh entry after a result.
                is CalcKey.Digit -> if (s.isResult) edited(s, key.ch) else edited(s, s.expression + key.ch)

                is CalcKey.Op -> {
                    val normalized = normalizeArithmeticOperatorAppend(s.expression, key.ch)
                    if (normalized != null) edited(s, normalized) else insert(s, key.ch)
                }

                is CalcKey.Func -> insert(s, key.insert)
            }
        }
    }

    /** Postfix inserts continue from a result (`5` → `5!`); anything else starts fresh. */
    private fun insert(s: CalculatorState, text: String): CalculatorState =
        if (s.isResult && text !in postfixInserts) edited(s, text) else edited(s, s.expression + text)

    private fun edited(s: CalculatorState, expr: String): CalculatorState =
        s.copy(
            expression = expr,
            preview = recompute(expr, s.degreesMode),
            resultDisplay = "",
            evaluatedExpression = "",
            error = "",
            isResult = false,
        )

    private fun evaluate(s: CalculatorState): CalculatorState {
        if (s.expression.isBlank()) return s
        val expr = closeParens(s.expression)
        return try {
            val result = RustBridge.calcEval(expr, s.degreesMode)
            viewModelScope.launch {
                historyDao.insert(
                    HistoryEntry(
                        expression = expr,
                        result = result.display,
                        source = "calculator",
                    ),
                )
            }
            s.copy(
                expression = result.refeed,
                preview = "",
                resultDisplay = result.display,
                evaluatedExpression = expr,
                error = "",
                isResult = true,
            )
        } catch (e: Exception) {
            s.copy(error = e.message.orEmpty().ifEmpty { "error" })
        }
    }

    private fun recompute(expr: String, degrees: Boolean): String {
        if (expr.isBlank()) return ""
        return try {
            RustBridge.calcEval(closeParens(expr), degrees).display
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private val arithmeticOperators = setOf("+", "-", "×", "÷")
        private val postfixInserts = setOf(")", "%", "!", "^2", "^")

        /**
         * If [ch] is a four-function operator, return the expression after
         * applying "replace last operator" / append rules; otherwise null.
         * Only `-` may start an expression or follow `(` (a negative number).
         */
        internal fun normalizeArithmeticOperatorAppend(expression: String, ch: String): String? {
            if (ch !in arithmeticOperators) return null
            val canStartHere = { base: String -> base.isEmpty() || base.endsWith("(") }
            return when {
                expression.takeLast(1) in arithmeticOperators -> {
                    val base = expression.dropLast(1)
                    if (canStartHere(base) && ch != "-") base else base + ch
                }
                canStartHere(expression) && ch != "-" -> expression
                else -> expression + ch
            }
        }

        /** Drop the last character, or a whole function token such as `sin(`. */
        internal fun deleteLast(expression: String): String {
            val token = functionInserts.firstOrNull { expression.endsWith(it) }
            return expression.dropLast(token?.length ?: 1)
        }

        /** Append the `)` the user has not typed yet, so `sin(30` evaluates. */
        internal fun closeParens(expression: String): String {
            val open = expression.count { it == '(' } - expression.count { it == ')' }
            return if (open > 0) expression + ")".repeat(open) else expression
        }
    }
}
