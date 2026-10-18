package com.calcrux.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcrux.data.HistoryDao
import com.calcrux.data.HistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: HistoryDao,
) : ViewModel() {

    val entries: StateFlow<List<HistoryEntry>> = dao.allEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch { dao.delete(entry) }
    }
}
