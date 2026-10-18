package com.calcrux.ui.loan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.R
import com.calcrux.ui.shared.TabularNumbers

@Composable
fun LoanScreen(
    vm: LoanViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            LoanInputField(
                label = stringResource(R.string.loan_principal),
                value = state.principalText,
                onValueChange = vm::onPrincipal,
            )
        }
        item {
            LoanInputField(
                label = stringResource(R.string.loan_annual_rate),
                value = state.rateText,
                onValueChange = vm::onRate,
            )
        }
        item {
            LoanInputField(
                label = stringResource(R.string.loan_months),
                value = state.monthsText,
                onValueChange = vm::onMonths,
                keyboardType = KeyboardType.Number,
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.methodIsEqualPayment,
                    onClick = { vm.selectMethod(true) },
                    label = { Text(stringResource(R.string.loan_equal_payment)) },
                    modifier = Modifier.weight(1f),
                    colors = methodChipColors(),
                )
                FilterChip(
                    selected = !state.methodIsEqualPayment,
                    onClick = { vm.selectMethod(false) },
                    label = { Text(stringResource(R.string.loan_equal_principal)) },
                    modifier = Modifier.weight(1f),
                    colors = methodChipColors(),
                )
            }
        }
        item {
            Text(
                stringResource(
                    if (state.methodIsEqualPayment) R.string.loan_equal_payment_desc
                    else R.string.loan_equal_principal_desc,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Button(onClick = vm::calculate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.loan_calculate))
            }
        }

        if (state.error.isNotEmpty()) {
            item {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        state.schedule?.let { sched ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SummaryRow(
                            // Equal-principal payments shrink each month; label the first one.
                            stringResource(
                                if (state.methodIsEqualPayment) R.string.loan_monthly else R.string.loan_first_month,
                            ),
                            money(sched.instalments.firstOrNull()?.payment ?: 0.0),
                        )
                        SummaryRow(stringResource(R.string.loan_total_interest), money(sched.totalInterest))
                        SummaryRow(stringResource(R.string.loan_total_payment), money(sched.totalPayment))
                    }
                }
            }

            item {
                ScheduleRow(
                    listOf(
                        stringResource(R.string.loan_col_period),
                        stringResource(R.string.loan_col_payment),
                        stringResource(R.string.loan_col_principal),
                        stringResource(R.string.loan_col_interest),
                        stringResource(R.string.loan_col_balance),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
            }

            items(sched.instalments) { inst ->
                ScheduleRow(
                    listOf(
                        "${inst.period}",
                        money(inst.payment),
                        money(inst.principalPart),
                        money(inst.interestPart),
                        money(inst.balance),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// Same selected style as the unit-category chips.
@Composable
private fun methodChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun LoanInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// Period column is narrow; balance gets extra room for 8-digit amounts.
private val COLUMN_WEIGHTS = listOf(0.6f, 1f, 1f, 1f, 1.3f)

@Composable
private fun ScheduleRow(
    cells: List<String>,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                modifier = Modifier.weight(COLUMN_WEIGHTS[i]),
                style = style.merge(TabularNumbers),
                color = color,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

private fun money(v: Double): String = "%,.2f".format(v)
