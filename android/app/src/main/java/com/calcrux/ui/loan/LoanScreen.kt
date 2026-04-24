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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.R

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
                )
                FilterChip(
                    selected = !state.methodIsEqualPayment,
                    onClick = { vm.selectMethod(false) },
                    label = { Text(stringResource(R.string.loan_equal_principal)) },
                    modifier = Modifier.weight(1f),
                )
            }
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
                            stringResource(R.string.loan_monthly),
                            "%.2f".format(sched.instalments.firstOrNull()?.payment ?: 0.0),
                        )
                        SummaryRow(
                            stringResource(R.string.loan_total_interest),
                            "%.2f".format(sched.totalInterest),
                        )
                        SummaryRow(
                            stringResource(R.string.loan_total_payment),
                            "%.2f".format(sched.totalPayment),
                        )
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    listOf(
                        stringResource(R.string.loan_col_period),
                        stringResource(R.string.loan_col_payment),
                        stringResource(R.string.loan_col_principal),
                        stringResource(R.string.loan_col_interest),
                        stringResource(R.string.loan_col_balance),
                    ).forEach { h ->
                        Text(h, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                HorizontalDivider()
            }

            items(sched.instalments) { inst ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("${inst.period}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("%.2f".format(inst.payment), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("%.2f".format(inst.principalPart), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("%.2f".format(inst.interestPart), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("%.2f".format(inst.balance), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

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
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
