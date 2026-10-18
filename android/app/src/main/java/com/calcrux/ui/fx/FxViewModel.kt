package com.calcrux.ui.fx

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcrux.data.FxService
import com.calcrux.ui.shared.appendDecimal
import com.calcrux.ui.shared.appendDigit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.MathContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.RoundingMode
import javax.inject.Inject

enum class FxStatus { Loading, Ok, Offline }

@Stable
data class FxState(
    val rates: Map<String, Double> = FALLBACK_RATES,
    val codes: List<String> = emptyList(),
    val pinnedCodes: Set<String> = DEFAULT_PINS,
    /** Currencies shown as rows; any row can be the input. */
    val rows: List<String> = DEFAULT_ROWS,
    val activeIndex: Int = 0,
    val inputText: String = "",
    /** Display value per row; the active row holds [inputText]. */
    val values: List<String> = DEFAULT_ROWS.map { "" },
    val status: FxStatus = FxStatus.Loading,
    val lastUpdated: String = BUILT_IN_LABEL,
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
            val (rates, date) = loadCachedRates() ?: (FALLBACK_RATES to BUILT_IN_LABEL)
            val rows = loadRows(rates.keys)
            FxState(
                rates = rates,
                lastUpdated = date,
                pinnedCodes = pins,
                codes = sortedCodes(rates.keys, pins),
                rows = rows,
                values = rows.map { "" },
            )
        }
    )
    val state = _state.asStateFlow()

    private var lastFetchMs = 0L

    init {
        refresh()
    }

    private fun loadPins(): Set<String> =
        HashSet(prefs.getStringSet(KEY_PINS, DEFAULT_PINS) ?: DEFAULT_PINS)

    private fun savePins(pins: Set<String>) {
        prefs.edit().putStringSet(KEY_PINS, pins).apply()
    }

    private fun loadRows(known: Set<String>): List<String> =
        prefs.getString(KEY_ROWS, null)?.split(',')?.filter { it in known }?.distinct()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_ROWS

    private fun saveRows(rows: List<String>) {
        prefs.edit().putString(KEY_ROWS, rows.joinToString(",")).apply()
    }

    /** Last successful download, so offline starts don't fall back to 2024 rates. */
    private fun loadCachedRates(): Pair<Map<String, Double>, String>? {
        val date = prefs.getString(KEY_RATES_DATE, null) ?: return null
        val cached = prefs.getString(KEY_RATES, null).orEmpty().split(',').mapNotNull { entry ->
            val (code, rate) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            rate.toDoubleOrNull()?.takeIf(::isValidRate)?.let { code to it }
        }.toMap()
        return if (cached.isEmpty()) null else (FALLBACK_RATES + cached) to date
    }

    private fun saveRates(rates: Map<String, Double>, date: String) {
        prefs.edit()
            .putString(KEY_RATES, rates.entries.joinToString(",") { "${it.key}=${it.value}" })
            .putString(KEY_RATES_DATE, date)
            .apply()
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

    /** Called when the FX page is shown; skips the network if rates were fetched recently. */
    fun refreshIfStale() {
        if (_state.value.status == FxStatus.Loading) return
        if (System.currentTimeMillis() - lastFetchMs < STALE_AFTER_MS) return
        refresh()
    }

    fun refresh() {
        lastFetchMs = System.currentTimeMillis()
        viewModelScope.launch {
            _state.update { it.copy(status = FxStatus.Loading, error = "") }
            try {
                val resp = service.latest("USD")
                val live = resp.rates.filterValues(::isValidRate) + ("USD" to 1.0)
                require(resp.result == "success" && resp.baseCode == "USD" && live.size > 1) {
                    "unexpected FX response"
                }
                val date = UPDATED_FORMAT.format(Instant.ofEpochSecond(resp.lastUpdateUnix))
                saveRates(live, date)
                // Keep built-in codes the feed might drop, so saved rows never disappear.
                val merged = FALLBACK_RATES + live
                _state.update { s ->
                    s.copy(
                        rates = merged,
                        codes = sortedCodes(merged.keys.toSet(), s.pinnedCodes),
                        status = FxStatus.Ok,
                        lastUpdated = date,
                        error = "",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep whatever rates we already have (live, cached or built-in).
                _state.update { it.copy(status = FxStatus.Offline, error = "") }
            }
            recalculate()
        }
    }

    /** Make [index] the input row, starting from the value it currently shows. */
    fun setActive(index: Int) {
        _state.update { s ->
            if (index !in s.rows.indices || index == s.activeIndex) return@update s
            s.copy(
                activeIndex = index,
                inputText = s.values[index].takeIf { it.toDoubleOrNull() != null } ?: "",
            )
        }
        recalculate()
    }

    /** Change a row's currency; picking one already shown swaps the two rows. */
    fun selectCode(index: Int, code: String) {
        val rows = _state.value.rows
        if (index !in rows.indices) return
        val other = rows.indexOf(code)
        val updated = rows.toMutableList().apply {
            if (other >= 0) this[other] = rows[index]
            this[index] = code
        }
        setRows(updated, _state.value.activeIndex)
    }

    /** Long-press reorder; the active row follows its currency. */
    fun moveRow(from: Int, to: Int) {
        val s = _state.value
        if (from !in s.rows.indices || to !in s.rows.indices || from == to) return
        val activeCode = s.rows[s.activeIndex]
        val rows = s.rows.toMutableList().apply { add(to, removeAt(from)) }
        setRows(rows, rows.indexOf(activeCode))
    }

    fun addRow(code: String) {
        val s = _state.value
        if (code in s.rows) return
        setRows(s.rows + code, s.activeIndex)
    }

    fun removeRow(index: Int) {
        val s = _state.value
        if (s.rows.size <= 1 || index !in s.rows.indices) return
        val active = when {
            index < s.activeIndex -> s.activeIndex - 1
            index == s.activeIndex -> 0
            else -> s.activeIndex
        }
        if (index == s.activeIndex) _state.update { it.copy(inputText = "") }
        setRows(s.rows.filterIndexed { i, _ -> i != index }, active)
    }

    private fun setRows(rows: List<String>, activeIndex: Int) {
        saveRows(rows)
        _state.update { it.copy(rows = rows, activeIndex = activeIndex) }
        recalculate()
    }

    fun onNumKey(digit: String) = onInput(appendDigit(_state.value.inputText, digit))

    fun onDecimalKey() = onInput(appendDecimal(_state.value.inputText))

    fun onBackspaceKey() = onInput(_state.value.inputText.dropLast(1))

    fun onClear() = onInput("")

    private fun onInput(text: String) {
        _state.update { it.copy(inputText = text, error = "") }
        recalculate()
    }

    private fun recalculate() {
        _state.update { s -> s.copy(values = convertRows(s)) }
    }
}

/** Every row converted from the active row's amount; blank when there's no amount. */
internal fun convertRows(s: FxState): List<String> {
    val amount = s.inputText.toDoubleOrNull()
    val fromRate = s.rates[s.rows.getOrNull(s.activeIndex)]
    return s.rows.mapIndexed { i, code ->
        val toRate = s.rates[code]
        when {
            i == s.activeIndex -> s.inputText
            amount == null || fromRate == null || toRate == null -> ""
            else -> (amount * (toRate / fromRate)).formatFxResult()
        }
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

private fun isValidRate(rate: Double): Boolean = rate.isFinite() && rate > 0.0

internal val DEFAULT_ROWS: List<String> = listOf("TWD", "USD", "JPY", "EUR", "CNY")
internal val DEFAULT_PINS: Set<String> = setOf("TWD", "USD", "EUR", "JPY", "CNY")
private const val PREFS_NAME = "fx_prefs"
private const val KEY_PINS = "pinned_codes"
private const val KEY_ROWS = "fx_rows"
// The feed updates daily; re-check at most every 30 minutes when the page is opened.
private const val STALE_AFTER_MS = 30 * 60 * 1000L
private const val KEY_RATES = "cached_rates"
private const val KEY_RATES_DATE = "cached_rates_date"
private const val BUILT_IN_LABEL = "內建匯率"
private val UPDATED_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

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
