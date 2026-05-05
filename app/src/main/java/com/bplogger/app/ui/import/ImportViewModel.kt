package com.bplogger.app.ui.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.data.repository.BpRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class BloodPressureRecord(
    var dateTime: String,
    var systolic: String,
    var diastolic: String,
    var pulse: String
)

data class CameraImportRow(
    val date: String,       // "MMdd" e.g. "0130" (Jan 30)
    val time: String,       // "HHmm" e.g. "0700"
    val systolic: String,
    val diastolic: String,
    val pulse: String,
    val year: String        // "2025"
)

class ImportViewModel(private val repository: BpRepository) : ViewModel() {

    fun saveRecords(records: List<BloodPressureRecord>) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val bpRecords = records.mapNotNull {
                try {
                    val timestamp = sdf.parse(it.dateTime)?.time ?: System.currentTimeMillis()
                    BpRecord(
                        systolic = it.systolic.toInt(),
                        diastolic = it.diastolic.toInt(),
                        heartRate = it.pulse.toInt(),
                        timestamp = timestamp,
                        source = "manual"
                    )
                } catch (e: Exception) {
                    null
                }
            }
            repository.insertAll(bpRecords)
        }
    }

    fun saveCameraRecords(rows: List<CameraImportRow>, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val bpRecords = rows.mapNotNull { row ->
                    try {
                        val month = row.date.substring(0, 2).toInt()
                        val day = row.date.substring(2, 4).toInt()
                        val hour = row.time.substring(0, 2).toInt()
                        val minute = row.time.substring(2, 4).toInt()
                        val year = row.year.toInt()

                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month - 1) // Calendar months are 0-based
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        BpRecord(
                            systolic = row.systolic.toInt(),
                            diastolic = row.diastolic.toInt(),
                            heartRate = row.pulse.toInt(),
                            timestamp = cal.timeInMillis,
                            source = "ocr"
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bpRecords.isEmpty()) {
                    onResult(false, "No valid records to save")
                    return@launch
                }

                repository.insertAll(bpRecords)
                onResult(true, "${bpRecords.size} record(s) saved")
            } catch (e: Exception) {
                onResult(false, "Save failed: ${e.message}")
            }
        }
    }
}