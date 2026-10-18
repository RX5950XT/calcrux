package com.calcrux.ui.convert

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.calcrux.data.RustBridge
import com.calcrux.ui.shared.appendDecimal
import com.calcrux.ui.shared.appendDigit
import com.calcrux.ui.shared.toggleSign
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
        _state.value = ConvertState(categories = cats)
        cats.firstOrNull()?.let(::selectCategory)
    }

    fun selectCategory(cat: String) {
        val units = sortedUnits(cat, RustBridge.unitList(cat) ?: emptyList())
        val (from, to) = DEFAULT_UNIT_PAIR[cat]?.takeIf { (f, t) -> f in units && t in units }
            ?: ((units.firstOrNull() ?: "") to (units.getOrNull(1) ?: units.firstOrNull() ?: ""))
        _state.update {
            it.copy(
                category = cat,
                units = units,
                fromUnit = from,
                toUnit = to,
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

    fun onNumKey(digit: String) = editInput { appendDigit(it, digit) }

    fun onDecimalKey() = editInput(::appendDecimal)

    fun onBackspaceKey() = editInput { it.dropLast(1) }

    fun onClear() = editInput { "" }

    /** Only temperatures go below zero. */
    fun onToggleSign() = editInput(::toggleSign)

    private fun editInput(edit: (String) -> String) {
        _state.update { it.copy(fromText = edit(it.fromText), error = "") }
        recalculate()
    }

    fun swap() {
        _state.update { s ->
            s.copy(
                fromUnit = s.toUnit,
                toUnit = s.fromUnit,
                // The result may be an error marker; never carry that into the input.
                fromText = s.toText.takeIf { it.toDoubleOrNull() != null } ?: "",
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
            _state.update { it.copy(toText = "", error = "換算錯誤") }
        }
    }
}

/** Everyday pair shown when a category opens (instead of e.g. pm → Å). */
private val DEFAULT_UNIT_PAIR = mapOf(
    "length" to ("m" to "ft"),
    "area" to ("sq.m" to "sq.ft"),
    "volume" to ("l" to "ml"),
    "weight" to ("kg" to "lb"),
    "velocity" to ("kmph" to "mileph"),
    "time" to ("h" to "min"),
    "temperature" to ("C" to "F"),
    "data" to ("mb" to "kb"),
)

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
