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
}
