package com.bplogger.app.ui.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bplogger.app.data.db.Medication
import com.bplogger.app.data.repository.MedicationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MedicationViewModel(private val repository: MedicationRepository) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deletedMedication = MutableStateFlow<Medication?>(null)
    val deletedMedication: StateFlow<Medication?> = _deletedMedication

    fun addMedication(medication: Medication) {
        viewModelScope.launch { repository.insert(medication) }
    }

    fun updateMedication(medication: Medication) {
        viewModelScope.launch { repository.update(medication) }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            _deletedMedication.value = medication
            repository.delete(medication)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _deletedMedication.value?.let {
                repository.insert(it)
                _deletedMedication.value = null
            }
        }
    }

    fun clearDeletedMedication() {
        _deletedMedication.value = null
    }

    fun toggleActive(medication: Medication) {
        viewModelScope.launch {
            repository.update(medication.copy(isActive = !medication.isActive))
        }
    }
}
