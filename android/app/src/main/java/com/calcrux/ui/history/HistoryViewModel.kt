package com.calcrux.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcrux.data.HistoryDao
import com.calcrux.data.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: HistoryDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<HistoryEntry>> = _query
        .flatMapLatest { q ->
            if (q.isBlank()) dao.allEntries() else dao.search(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch { dao.delete(entry) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.clearAll() }
    }
}
