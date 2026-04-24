package com.calcrux.data

import uniffi.calcrux.amortizeEqualPayment as rustAmortizeEqualPayment
import uniffi.calcrux.amortizeEqualPrincipal as rustAmortizeEqualPrincipal
import uniffi.calcrux.calcEval as rustCalcEval
import uniffi.calcrux.convertUnit as rustConvertUnit
import uniffi.calcrux.calcruxVersion as rustCalcruxVersion
import uniffi.calcrux.unitCategories as rustUnitCategories
import uniffi.calcrux.unitList as rustUnitList

object RustBridge {

    fun calcEval(expression: String, degreesMode: Boolean): String =
        rustCalcEval(expression, degreesMode)

    fun unitCategories(): List<String> =
        rustUnitCategories()

    fun unitList(category: String): List<String>? =
        rustUnitList(category)

    fun convertUnit(category: String, from: String, to: String, value: Double): Double =
        rustConvertUnit(category, from, to, value)

    fun amortizeEqualPayment(
        principal: Double,
        annualRatePct: Double,
        months: UInt,
    ) = rustAmortizeEqualPayment(principal, annualRatePct, months)

    fun amortizeEqualPrincipal(
        principal: Double,
        annualRatePct: Double,
        months: UInt,
    ) = rustAmortizeEqualPrincipal(principal, annualRatePct, months)

    fun version(): String = rustCalcruxVersion()
}
