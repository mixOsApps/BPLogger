package com.bplogger.app.data.export

import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.domain.model.BpClassifier
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {

    fun export(records: List<BpRecord>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val sb = StringBuilder()
        sb.appendLine("Date,Time,Systolic,Diastolic,HeartRate,Weight,SpO2,Classification,Notes,Source")

        records.sortedByDescending { it.timestamp }.forEach { record ->
            val date = Date(record.timestamp)
            val classification = BpClassifier.classify(record.systolic, record.diastolic).label
            val weightStr = record.weight?.let { "%.1f".format(it) } ?: ""
            val spO2Str = record.spO2?.toString() ?: ""
            val notes = record.notes.replace(",", ";").replace("\n", " ")

            sb.appendLine(
                "${dateFormat.format(date)},${timeFormat.format(date)}," +
                "${record.systolic},${record.diastolic},${record.heartRate}," +
                "$weightStr,$spO2Str,$classification,$notes,${record.source}"
            )
        }

        return sb.toString()
    }
}
