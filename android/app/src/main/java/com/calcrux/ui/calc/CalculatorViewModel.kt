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
    val expression: String = "",
    val preview: String = "",
    val error: String = "",
    val degreesMode: Boolean = true,
    val invMode: Boolean = false,
    val scientificMode: Boolean = true,
)

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val historyDao: HistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(CalculatorState())
    val state = _state.asStateFlow()

    fun toggleAngleMode() {
        _state.update { it.copy(degreesMode = !it.degreesMode, preview = recompute(it.expression, !it.degreesMode)) }
    }

    fun toggleInvMode() {
        _state.update { it.copy(invMode = !it.invMode) }
    }

    fun toggleScientificMode() {
        _state.update { it.copy(scientificMode = !it.scientificMode) }
    }

    fun setExpression(expr: String) {
        _state.update { s ->
            s.copy(expression = expr, preview = recompute(expr, s.degreesMode), error = "")
        }
    }

    fun onKey(key: CalcKey) {
        _state.update { s ->
            when (key) {
                CalcKey.Clear     -> s.copy(expression = "", preview = "", error = "")
                CalcKey.ClearAll  -> s.copy(expression = "", preview = "", error = "")
                CalcKey.Backspace -> {
                    val expr = s.expression.dropLast(1)
                    s.copy(expression = expr, preview = recompute(expr, s.degreesMode), error = "")
                }
                CalcKey.Equals   -> evaluate(s)
                is CalcKey.Digit -> append(s, key.ch)
                is CalcKey.Op    -> append(s, key.ch)
                is CalcKey.Func  -> append(s, key.insert)
            }
        }
    }

    private fun append(s: CalculatorState, ch: String): CalculatorState {
        val expr = s.expression + ch
        return s.copy(
            expression = expr,
            preview = recompute(expr, s.degreesMode),
            error = "",
        )
    }

    private fun evaluate(s: CalculatorState): CalculatorState {
        if (s.expression.isBlank()) return s
        return try {
            val result = RustBridge.calcEval(s.expression, s.degreesMode)
            viewModelScope.launch {
                historyDao.insert(HistoryEntry(expression = s.expression, result = result, source = "calculator"))
            }
            s.copy(expression = result, preview = "", error = "")
        } catch (e: Exception) {
            s.copy(error = e.message?.take(80) ?: "Error")
        }
    }

    private fun recompute(expr: String, degrees: Boolean): String {
        if (expr.isBlank()) return ""
        return try {
            RustBridge.calcEval(expr, degrees)
        } catch (_: Exception) {
            ""
        }
    }
}
