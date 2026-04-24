package com.calcrux.ui.convert

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.calcrux.data.RustBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject

@Stable
data class ConvertState(
    val categories: List<String> = emptyList(),
    val category: String = "",
    val units: List<String> = emptyList(),
    val fromUnit: String = "",
    val toUnit: String = "",
    val fromText: String = "",
    val toText: String = "",
    val error: String = "",
)

@HiltViewModel
class ConvertViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ConvertState())
    val state = _state.asStateFlow()

    init {
        val rustCats = RustBridge.unitCategories()
        // Apply desired display order; any extra categories from Rust fall to the end.
        val cats = CATEGORY_ORDER.filter { it in rustCats } +
                   rustCats.filterNot { it in CATEGORY_ORDER }
        val first = cats.firstOrNull() ?: ""
        val units = if (first.isNotEmpty()) sortedUnits(first, RustBridge.unitList(first) ?: emptyList()) else emptyList()
        _state.value = ConvertState(
            categories = cats,
            category = first,
            units = units,
            fromUnit = units.firstOrNull() ?: "",
            toUnit = units.getOrNull(1) ?: units.firstOrNull() ?: "",
        )
    }

    fun selectCategory(cat: String) {
        val units = sortedUnits(cat, RustBridge.unitList(cat) ?: emptyList())
        _state.update {
            it.copy(
                category = cat,
                units = units,
                fromUnit = units.firstOrNull() ?: "",
                toUnit = units.getOrNull(1) ?: units.firstOrNull() ?: "",
                fromText = "",
                toText = "",
                error = "",
            )
        }
    }

    private fun sortedUnits(category: String, raw: List<String>): List<String> {
        val order = UNIT_PHYSICAL_ORDER[category] ?: return raw
        return raw.sortedBy { unit -> order.indexOf(unit).let { if (it < 0) Int.MAX_VALUE else it } }
    }

    fun selectFrom(unit: String) {
        _state.update { it.copy(fromUnit = unit) }
        recalculate()
    }

    fun selectTo(unit: String) {
        _state.update { it.copy(toUnit = unit) }
        recalculate()
    }

    // ── NumPad input handlers ─────────────────────────────────────────────────

    fun onNumKey(digit: String) {
        val cur = _state.value.fromText
        if (cur.length >= 18) return
        // Prevent leading zeros: "0" + "5" → "5", but "0." + "5" is fine
        val newText = if (cur == "0" && digit != ".") digit else cur + digit
        _state.update { it.copy(fromText = newText, error = "") }
        recalculate()
    }

    fun onDecimalKey() {
        val cur = _state.value.fromText
        if ('.' in cur) return
        val newText = if (cur.isEmpty()) "0." else "$cur."
        _state.update { it.copy(fromText = newText, error = "") }
        recalculate()
    }

    fun onBackspaceKey() {
        val cur = _state.value.fromText
        if (cur.isEmpty()) return
        val newText = cur.dropLast(1)
        _state.update { it.copy(fromText = newText, error = "") }
        recalculate()
    }

    fun swap() {
        _state.update { s ->
            s.copy(
                fromUnit = s.toUnit,
                toUnit = s.fromUnit,
                fromText = s.toText,
                toText = s.fromText,
            )
        }
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val value = s.fromText.toDoubleOrNull()
        if (value == null) {
            _state.update { it.copy(toText = "", error = "") }
            return
        }
        try {
            val r = RustBridge.convertUnit(s.category, s.fromUnit, s.toUnit, value)
            _state.update { it.copy(toText = r.formatResult(), error = "") }
        } catch (e: Exception) {
            _state.update { it.copy(toText = "", error = e.message?.take(60) ?: "換算錯誤") }
        }
    }
}

private val CATEGORY_ORDER = listOf(
    "length", "area", "volume", "weight", "velocity", "time", "temperature", "data",
)

/** Round to [sigFigs] significant figures and strip trailing zeros. */
internal fun Double.formatResult(sigFigs: Int = 10): String {
    if (isNaN() || isInfinite()) return "錯誤"
    if (this == 0.0) return "0"
    return toBigDecimal()
        .round(MathContext(sigFigs, RoundingMode.HALF_UP))
        .stripTrailingZeros()
        .toPlainString()
}
