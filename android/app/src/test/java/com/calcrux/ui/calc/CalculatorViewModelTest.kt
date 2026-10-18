package com.calcrux.ui.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorViewModelTest {
    @Test
    fun `連續點不同運算符會直接替換`() {
        val result = CalculatorViewModel.normalizeArithmeticOperatorAppend("12+", "×")

        assertEquals("12×", result)
    }

    @Test
    fun `連續點相同運算符不會重複`() {
        val result = CalculatorViewModel.normalizeArithmeticOperatorAppend("12+", "+")

        assertEquals("12+", result)
    }

    @Test
    fun `空字串時點四則運算符不會插入`() {
        val result = CalculatorViewModel.normalizeArithmeticOperatorAppend("", "÷")

        assertEquals("", result)
    }

    @Test
    fun `非四則符號交回原本 append 流程`() {
        val result = CalculatorViewModel.normalizeArithmeticOperatorAppend("12", ".")

        assertNull(result)
    }

    @Test
    fun `空字串時可以輸入負號`() {
        assertEquals("-", CalculatorViewModel.normalizeArithmeticOperatorAppend("", "-"))
        assertEquals("(-", CalculatorViewModel.normalizeArithmeticOperatorAppend("(", "-"))
    }

    @Test
    fun `負號後改點乘號會移除負號而不是變成非法算式`() {
        assertEquals("(", CalculatorViewModel.normalizeArithmeticOperatorAppend("(-", "×"))
        assertEquals("", CalculatorViewModel.normalizeArithmeticOperatorAppend("-", "+"))
    }

    @Test
    fun `退格一次刪除整個函數名`() {
        assertEquals("2+", CalculatorViewModel.deleteLast("2+sin("))
        assertEquals("", CalculatorViewModel.deleteLast("arcsin("))
        assertEquals("12", CalculatorViewModel.deleteLast("123"))
    }

    @Test
    fun `未閉合括號自動補上`() {
        assertEquals("sin(30)", CalculatorViewModel.closeParens("sin(30"))
        assertEquals("2×(3+(4))", CalculatorViewModel.closeParens("2×(3+(4"))
        assertEquals("(1/3)", CalculatorViewModel.closeParens("(1/3)"))
    }
}
