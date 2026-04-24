package com.calcrux.ui.loan

import androidx.lifecycle.ViewModel
import com.calcrux.data.RustBridge
import uniffi.opencalc.LoanSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LoanState(
    val principalText: String = "200000",
    val rateText: String = "4.5",
    val monthsText: String = "360",
    val methodIsEqualPayment: Boolean = true,
    val schedule: LoanSchedule? = null,
    val error: String = "",
)

@HiltViewModel
class LoanViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LoanState())
    val state = _state.asStateFlow()

    fun onPrincipal(v: String) = _state.update { it.copy(principalText = v, error = "") }
    fun onRate(v: String)      = _state.update { it.copy(rateText = v, error = "") }
    fun onMonths(v: String)    = _state.update { it.copy(monthsText = v, error = "") }
    fun selectMethod(isEqual: Boolean) = _state.update { it.copy(methodIsEqualPayment = isEqual, schedule = null) }

    fun calculate() {
        val s = _state.value
        val principal = s.principalText.toDoubleOrNull()
        val rate      = s.rateText.toDoubleOrNull()
        val months    = s.monthsText.toUIntOrNull()

        if (principal == null || rate == null || months == null) {
            _state.update { it.copy(error = "Please enter valid numbers") }
            return
        }

        try {
            val sched = if (s.methodIsEqualPayment)
                RustBridge.amortizeEqualPayment(principal, rate, months)
            else
                RustBridge.amortizeEqualPrincipal(principal, rate, months)
            _state.update { it.copy(schedule = sched, error = "") }
        } catch (e: Exception) {
            _state.update { it.copy(schedule = null, error = e.message?.take(80) ?: "Error") }
        }
    }
}
