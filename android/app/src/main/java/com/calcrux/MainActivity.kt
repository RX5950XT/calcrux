package com.calcrux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.ui.calc.CalculatorScreen
import com.calcrux.ui.calc.CalculatorViewModel
import com.calcrux.ui.convert.ConvertScreen
import com.calcrux.ui.fx.FxScreen
import com.calcrux.ui.history.HistoryScreen
import com.calcrux.ui.loan.LoanScreen
import com.calcrux.ui.theme.CalcRuxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalcRuxTheme {
                CalcRuxNavHost()
            }
        }
    }
}

@Composable
fun CalcRuxNavHost() {
    val mainTabs = listOf(
        stringResource(R.string.tab_calculator),
        stringResource(R.string.tab_convert),
        stringResource(R.string.tab_history),
    )
    val mainPagerState = rememberPagerState { mainTabs.size }
    val scope = rememberCoroutineScope()
    val calcVm: CalculatorViewModel = hiltViewModel()

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Tab click → scrollToPage (instant). Only the indicator's built-in animation plays,
            // avoiding the double-animation stutter from animateScrollToPage + indicator.
            TabRow(selectedTabIndex = mainPagerState.currentPage) {
                mainTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = mainPagerState.currentPage == index,
                        onClick = { scope.launch { mainPagerState.scrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }

            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                when (page) {
                    0 -> CalculatorScreen(vm = calcVm)
                    1 -> ConvertLoanPager()
                    2 -> HistoryScreen(
                        onRestoreExpression = { expr ->
                            calcVm.setExpression(expr)
                            scope.launch { mainPagerState.scrollToPage(0) }
                        },
                    )
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ── 換算 + 匯率 + 貸款 nested pager ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvertLoanPager() {
    val subTabs = listOf(
        stringResource(R.string.tab_unit_convert),
        stringResource(R.string.tab_fx),
        stringResource(R.string.tab_loan),
    )
    val subPagerState = rememberPagerState { subTabs.size }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = subPagerState.currentPage) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = subPagerState.currentPage == index,
                    onClick = { scope.launch { subPagerState.scrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }

        HorizontalPager(
            state = subPagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true,
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            when (page) {
                0 -> ConvertScreen()
                1 -> FxScreen()
                2 -> LoanScreen()
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}
