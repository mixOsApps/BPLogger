package com.bplogger.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.domain.model.BpCategory
import com.bplogger.app.domain.model.BpClassifier
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: BpRepository) : ViewModel() {

    private val allRecords: StateFlow<List<BpRecord>> = repository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFilter = MutableStateFlow<BpCategory?>(null)
    val selectedFilter: StateFlow<BpCategory?> = _selectedFilter

    val records: StateFlow<List<BpRecord>> = combine(allRecords, _selectedFilter) { records, filter ->
        if (filter == null) records
        else records.filter { BpClassifier.classify(it.systolic, it.diastolic) == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deletedRecord = MutableStateFlow<BpRecord?>(null)
    val deletedRecord: StateFlow<BpRecord?> = _deletedRecord

    fun setFilter(category: BpCategory?) {
        _selectedFilter.value = category
    }

    fun addRecord(record: BpRecord) {
        viewModelScope.launch { repository.insert(record) }
    }

    fun updateRecord(record: BpRecord) {
        viewModelScope.launch { repository.update(record) }
    }

    fun deleteRecord(record: BpRecord) {
        viewModelScope.launch {
            _deletedRecord.value = record
            repository.delete(record)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _deletedRecord.value?.let {
                repository.insert(it)
                _deletedRecord.value = null
            }
        }
    }

    fun clearDeletedRecord() {
        _deletedRecord.value = null
    }
}
