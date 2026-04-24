package com.calcrux.data

import uniffi.opencalc.amortizeEqualPayment as rustAmortizeEqualPayment
import uniffi.opencalc.amortizeEqualPrincipal as rustAmortizeEqualPrincipal
import uniffi.opencalc.calcEval as rustCalcEval
import uniffi.opencalc.convertUnit as rustConvertUnit
import uniffi.opencalc.calcruxVersion as rustCalcruxVersion
import uniffi.opencalc.unitCategories as rustUnitCategories
import uniffi.opencalc.unitList as rustUnitList

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
