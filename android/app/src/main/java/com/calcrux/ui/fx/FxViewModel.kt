package com.calcrux.ui.fx

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcrux.data.FxService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject

enum class FxStatus { Loading, Ok, Offline }

@Stable
data class FxState(
    val rates: Map<String, Double> = FALLBACK_RATES,
    val codes: List<String> = emptyList(),
    val pinnedCodes: Set<String> = DEFAULT_PINS,
    val fromCode: String = "USD",
    val toCode: String = "TWD",
    val fromText: String = "",
    val toText: String = "",
    val status: FxStatus = FxStatus.Loading,
    val lastUpdated: String = "內建匯率",
    val error: String = "",
)

@HiltViewModel
class FxViewModel @Inject constructor(
    private val service: FxService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(
        loadPins().let { pins ->
            FxState(
                pinnedCodes = pins,
                codes = sortedCodes(FALLBACK_RATES.keys.toSet(), pins),
            )
        }
    )
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    private fun loadPins(): Set<String> =
        HashSet(prefs.getStringSet(KEY_PINS, DEFAULT_PINS) ?: DEFAULT_PINS)

    private fun savePins(pins: Set<String>) {
        prefs.edit().putStringSet(KEY_PINS, pins).apply()
    }

    fun togglePin(code: String) {
        val updated = _state.value.pinnedCodes.toMutableSet()
        if (code in updated) updated.remove(code) else updated.add(code)
        savePins(updated)
        _state.update { s ->
            s.copy(
                pinnedCodes = updated,
                codes = sortedCodes(s.rates.keys.toSet(), updated),
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(status = FxStatus.Loading, error = "") }
            try {
                val resp = service.latest("USD")
                // Merge live rates ON TOP of fallback so all 61 currencies stay available.
                // frankfurter.app uses ECB data (~33 currencies) and excludes TWD, VND, etc.
                val merged = FALLBACK_RATES.toMutableMap()
                merged.putAll(resp.rates)
                merged["USD"] = 1.0
                _state.update { s ->
                    s.copy(
                        rates = merged,
                        codes = sortedCodes(merged.keys.toSet(), s.pinnedCodes),
                        status = FxStatus.Ok,
                        lastUpdated = resp.date,
                        error = "",
                    )
                }
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(
                        rates = FALLBACK_RATES,
                        codes = sortedCodes(FALLBACK_RATES.keys.toSet(), s.pinnedCodes),
                        status = FxStatus.Offline,
                        lastUpdated = "內建匯率",
                        error = "",
                    )
                }
            }
            recalculate()
        }
    }

    fun selectFrom(code: String) {
        _state.update { it.copy(fromCode = code) }
        recalculate()
    }

    fun selectTo(code: String) {
        _state.update { it.copy(toCode = code) }
        recalculate()
    }

    fun onNumKey(digit: String) {
        val cur = _state.value.fromText
        if (cur.length >= 18) return
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
        _state.update { it.copy(fromText = cur.dropLast(1), error = "") }
        recalculate()
    }

    fun onFromInput(text: String) {
        _state.update { it.copy(fromText = text, error = "") }
        recalculate()
    }

    fun swap() {
        _state.update { s ->
            s.copy(
                fromCode = s.toCode,
                toCode = s.fromCode,
                fromText = s.toText,
                toText = s.fromText,
            )
        }
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val amount = s.fromText.toDoubleOrNull() ?: return run {
            _state.update { it.copy(toText = "") }
        }
        val fromRate = s.rates[s.fromCode] ?: return
        val toRate = s.rates[s.toCode] ?: return
        if (fromRate <= 0.0) return
        val result = amount * (toRate / fromRate)
        _state.update { it.copy(toText = result.formatFxResult()) }
    }
}

/** Sorted list: pinned currencies first (alphabetical), then the rest (alphabetical). */
internal fun sortedCodes(all: Set<String>, pinned: Set<String>): List<String> {
    val pinnedSorted = pinned.filter { it in all }.sorted()
    val rest = all.filterNot { it in pinned }.sorted()
    return pinnedSorted + rest
}

/** Round to 6 significant figures for currency display. */
private fun Double.formatFxResult(): String {
    if (isNaN() || isInfinite()) return "錯誤"
    if (this == 0.0) return "0"
    return toBigDecimal()
        .round(MathContext(6, RoundingMode.HALF_UP))
        .stripTrailingZeros()
        .toPlainString()
}

internal val DEFAULT_PINS: Set<String> = setOf("TWD", "USD", "EUR", "JPY", "CNY")
private const val PREFS_NAME = "fx_prefs"
private const val KEY_PINS = "pinned_codes"

// Fallback rates (USD-based, approximate 2024-01)
private val FALLBACK_RATES: Map<String, Double> = mapOf(
    "AED" to 3.6725, "ARS" to 870.0,  "AUD" to 1.53,   "BDT" to 110.0,
    "BHD" to 0.376,  "BND" to 1.34,   "BRL" to 4.97,   "BYR" to 3.2,
    "CAD" to 1.36,   "CHF" to 0.90,   "CLP" to 920.0,  "CNH" to 7.24,
    "CNY" to 7.24,   "COP" to 3900.0, "CRC" to 518.0,  "CZK" to 22.8,
    "DKK" to 6.88,   "EGP" to 30.9,   "EUR" to 0.92,   "GBP" to 0.79,
    "HKD" to 7.82,   "HRK" to 6.94,   "HUF" to 355.0,  "IDR" to 15600.0,
    "ILS" to 3.72,   "INR" to 83.1,   "ISK" to 137.0,  "JOD" to 0.709,
    "JPY" to 148.5,  "KES" to 130.0,  "KHR" to 4090.0, "KRW" to 1325.0,
    "KWD" to 0.307,  "LAK" to 21000.0,"LBP" to 89500.0,"LKR" to 315.0,
    "MAD" to 10.0,   "MMK" to 2100.0, "MOP" to 8.06,   "MXN" to 17.2,
    "MYR" to 4.67,   "NOK" to 10.6,   "NZD" to 1.63,   "OMR" to 0.385,
    "PHP" to 56.5,   "PKR" to 279.0,  "PLN" to 3.98,   "QAR" to 3.64,
    "RON" to 4.57,   "SAR" to 3.75,   "SEK" to 10.4,   "SGD" to 1.34,
    "THB" to 35.0,   "TRY" to 32.0,   "TWD" to 31.8,   "UAH" to 38.0,
    "UGX" to 3780.0, "USD" to 1.0,    "VND" to 24500.0,"ZAR" to 18.8,
    "ZMW" to 26.0,
)
