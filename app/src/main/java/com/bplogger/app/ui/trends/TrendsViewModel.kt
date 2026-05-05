package com.bplogger.app.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.SettingsRepository
import com.bplogger.app.domain.model.BpClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class DateRange(val startMs: Long, val endMs: Long)

data class TrendStats(
    val avgSystolic: Double,
    val avgDiastolic: Double,
    val avgHeartRate: Double,
    val maxSystolic: Int,
    val minSystolic: Int,
    val maxDiastolic: Int,
    val minDiastolic: Int,
    val highAlertCount: Int,
    val totalRecords: Int,
    val avgWeight: Double? = null,
    val avgSpO2: Double? = null
)

class TrendsViewModel(
    private val repository: BpRepository,
    val settingsRepository: SettingsRepository
) : ViewModel() {

    // Default: last 30 days
    private val _dateRange = MutableStateFlow(
        DateRange(
            startMs = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000),
            endMs = System.currentTimeMillis()
        )
    )
    val dateRange: StateFlow<DateRange> = _dateRange

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<BpRecord>> = _dateRange.flatMapLatest { range ->
        repository.getRecordsByRange(range.startMs, range.endMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<TrendStats?> = records.map { list ->
        if (list.isEmpty()) return@map null
        val weightsWithValues = list.mapNotNull { it.weight }
        val spO2WithValues = list.mapNotNull { it.spO2 }
        TrendStats(
            avgSystolic = list.map { it.systolic }.average(),
            avgDiastolic = list.map { it.diastolic }.average(),
            avgHeartRate = list.map { it.heartRate }.average(),
            maxSystolic = list.maxOf { it.systolic },
            minSystolic = list.minOf { it.systolic },
            maxDiastolic = list.maxOf { it.diastolic },
            minDiastolic = list.minOf { it.diastolic },
            highAlertCount = list.count { BpClassifier.isHighAlert(it.systolic, it.diastolic) },
            totalRecords = list.size,
            avgWeight = if (weightsWithValues.isNotEmpty()) weightsWithValues.map { it.toDouble() }.average() else null,
            avgSpO2 = if (spO2WithValues.isNotEmpty()) spO2WithValues.map { it.toDouble() }.average() else null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun setDateRange(startMs: Long, endMs: Long) {
        _dateRange.value = DateRange(startMs, endMs)
    }

    fun setPreset(days: Int) {
        val end = System.currentTimeMillis()
        val start = end - (days.toLong() * 24 * 60 * 60 * 1000)
        _dateRange.value = DateRange(start, end)
    }
}
