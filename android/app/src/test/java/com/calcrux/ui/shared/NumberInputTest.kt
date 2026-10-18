package com.calcrux.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberInputTest {
    @Test
    fun `開頭的 0 會被取代而不是重複`() {
        assertEquals("5", appendDigit("0", "5"))
        assertEquals("0", appendDigit("", "00"))
        assertEquals("0", appendDigit("0", "00"))
        assertEquals("1200", appendDigit("12", "00"))
        assertEquals("0.5", appendDigit("0.", "5"))
        assertEquals("-5", appendDigit("-0", "5"))
    }

    @Test
    fun `長度上限不含負號`() {
        val full = "1".repeat(18)
        assertEquals(full, appendDigit(full, "2"))
        assertEquals("-$full", appendDigit("-" + "1".repeat(17), "1"))
    }

    @Test
    fun `小數點與正負號`() {
        assertEquals("0.", appendDecimal(""))
        assertEquals("-0.", appendDecimal("-"))
        assertEquals("1.5", appendDecimal("1.5"))
        assertEquals("-12", toggleSign("12"))
        assertEquals("12", toggleSign("-12"))
    }

    @Test
    fun `千分位只套用在整數部分`() {
        assertEquals("12,345.6789", groupDigits("12345.6789"))
        assertEquals("1,234+56,789×2", groupDigits("1234+56789×2"))
        assertEquals("0.123456", groupDigits("0.123456"))
        assertEquals("−1,000", prettyMath("-1000"))
    }
}
