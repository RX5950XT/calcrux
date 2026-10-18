package com.calcrux.ui.shared

import androidx.compose.ui.text.TextStyle

// Integer digit runs only: not preceded by a digit or a decimal point.
private val integerRun = Regex("(?<![.\\d])\\d{4,}")

/** Thousands separators for display: "12345.6789" → "12,345.6789". */
fun groupDigits(text: String): String =
    integerRun.replace(text) { m -> m.value.reversed().chunked(3).joinToString(",").reversed() }

/** Calculator display form: grouped digits and a true minus sign (U+2212). */
fun prettyMath(text: String): String = groupDigits(text).replace('-', '−')

/** Tabular figures so digits don't shift width as values change. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
