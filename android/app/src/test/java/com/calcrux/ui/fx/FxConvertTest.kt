package com.calcrux.ui.fx

import org.junit.Assert.assertEquals
import org.junit.Test

class FxConvertTest {
    private val rates = mapOf("USD" to 1.0, "TWD" to 32.0, "JPY" to 150.0)

    @Test
    fun `任一欄都可當輸入，其他欄跟著換算`() {
        val s = FxState(rates = rates, rows = listOf("TWD", "USD", "JPY"), activeIndex = 1, inputText = "10")
        assertEquals(listOf("320", "10", "1500"), convertRows(s))
        assertEquals(listOf("3200", "100", "15000"), convertRows(s.copy(activeIndex = 0, inputText = "3200")))
    }

    @Test
    fun `沒有輸入時其他欄留空`() {
        val s = FxState(rates = rates, rows = listOf("TWD", "USD"), inputText = "")
        assertEquals(listOf("", ""), convertRows(s))
    }

    @Test
    fun `國旗依幣別前兩碼`() {
        assertEquals("🇹🇼", currencyFlag("TWD"))
        assertEquals("🇪🇺", currencyFlag("EUR"))
        assertEquals("🌐", currencyFlag("XAF"))
    }
}
