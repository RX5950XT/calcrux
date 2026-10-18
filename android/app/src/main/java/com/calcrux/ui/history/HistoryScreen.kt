package com.calcrux.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calcrux.R
import com.calcrux.data.HistoryEntry
import com.calcrux.ui.shared.TabularNumbers
import com.calcrux.ui.shared.prettyMath
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Thread-safe and shared, instead of one SimpleDateFormat per list item.
private const val MAX_SHOWN_CHARS = 120

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    vm: HistoryViewModel = hiltViewModel(),
    onRestoreExpression: (String) -> Unit = {},
) {
    val entries by vm.entries.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Newest at the bottom, next to the handle you pull up to close.
            val listState = rememberLazyListState()
            // A new calculation lands at index 0; jump to it instead of keeping the old anchor.
            LaunchedEffect(entries.firstOrNull()?.id) { listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.id }, contentType = { "entry" }) { entry ->
                    HistoryCard(
                        modifier = Modifier.animateItem(),
                        entry = entry,
                        onDelete = { vm.delete(entry) },
                        onRestore = { onRestoreExpression(entry.expression) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    modifier: Modifier,
    entry: HistoryEntry,
    onDelete: () -> Unit,
    onRestore: () -> Unit = {},
) {
    val dateText = remember(entry.timestampMs) {
        DATE_FORMAT.format(Instant.ofEpochMilli(entry.timestampMs))
    }
    // Old entries can hold huge results (up to ~330k chars before the cycle cap); measuring
    // the whole string on every scroll-in is what made the list stutter. One line shows ~40.
    val expression = remember(entry.expression) { prettyMath(entry.expression.take(MAX_SHOWN_CHARS)) }
    val result = remember(entry.result) { "= ${prettyMath(entry.result.take(MAX_SHOWN_CHARS))}" }

    Card(
        onClick = onRestore,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    expression,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    result,
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete))
            }
        }
    }
}
