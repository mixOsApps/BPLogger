package com.bplogger.app.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.data.db.Medication
import com.bplogger.app.domain.model.BpCategory
import com.bplogger.app.domain.model.BpClassifier
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExporter {

    private const val PAGE_WIDTH = 595  // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 16f

    private val titlePaint = Paint().apply {
        textSize = 20f; isFakeBoldText = true; color = Color.parseColor("#D32F2F")
    }
    private val headerPaint = Paint().apply {
        textSize = 12f; isFakeBoldText = true; color = Color.DKGRAY
    }
    private val bodyPaint = Paint().apply {
        textSize = 10f; color = Color.DKGRAY
    }
    private val smallPaint = Paint().apply {
        textSize = 9f; color = Color.GRAY
    }
    private val linePaint = Paint().apply {
        color = Color.LTGRAY; strokeWidth = 0.5f
    }

    fun exportRecords(
        records: List<BpRecord>,
        outputStream: OutputStream,
        startDate: Long? = null,
        endDate: Long? = null
    ) {
        val filtered = records
            .filter { r -> (startDate == null || r.timestamp >= startDate) && (endDate == null || r.timestamp <= endDate) }
            .sortedByDescending { it.timestamp }

        val document = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        val rangeFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Title
        canvas.drawText("BP Logger Report", MARGIN, y + 20f, titlePaint)
        y += 30f

        // Date range
        val rangeText = if (startDate != null && endDate != null) {
            "${rangeFmt.format(Date(startDate))} - ${rangeFmt.format(Date(endDate))}"
        } else "All records"
        canvas.drawText(rangeText, MARGIN, y + 12f, smallPaint)
        y += 20f
        canvas.drawText("Total: ${filtered.size} records | Generated: ${rangeFmt.format(Date())}", MARGIN, y + 10f, smallPaint)
        y += 20f

        // Summary stats
        if (filtered.isNotEmpty()) {
            val avgSys = filtered.map { it.systolic }.average().toInt()
            val avgDia = filtered.map { it.diastolic }.average().toInt()
            val avgHr = filtered.map { it.heartRate }.average().toInt()
            val highCount = filtered.count { BpClassifier.isHighAlert(it.systolic, it.diastolic) }

            canvas.drawText("Averages: SYS $avgSys | DIA $avgDia | HR $avgHr | High Alerts: $highCount", MARGIN, y + 12f, headerPaint)
            y += 25f
        }

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 10f

        // Table header
        drawTableHeader(canvas, y)
        y += LINE_HEIGHT + 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 6f

        // Records
        for (record in filtered) {
            if (y > PAGE_HEIGHT - MARGIN - 20f) {
                document.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
                drawTableHeader(canvas, y)
                y += LINE_HEIGHT + 4f
                canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                y += 6f
            }

            val category = BpClassifier.classify(record.systolic, record.diastolic)
            val catPaint = Paint(bodyPaint).apply { color = categoryColor(category) }

            canvas.drawText(dateFormat.format(Date(record.timestamp)), MARGIN, y + 10f, bodyPaint)
            canvas.drawText("${record.systolic}/${record.diastolic}", 200f, y + 10f, bodyPaint)
            canvas.drawText("${record.heartRate}", 280f, y + 10f, bodyPaint)
            canvas.drawText(record.weight?.let { "%.1f".format(it) } ?: "-", 320f, y + 10f, bodyPaint)
            canvas.drawText(record.spO2?.toString() ?: "-", 365f, y + 10f, bodyPaint)
            canvas.drawText(category.label, 400f, y + 10f, catPaint)
            y += LINE_HEIGHT + 2f
        }

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    fun exportDoctorReport(
        records: List<BpRecord>,
        medications: List<Medication>,
        outputStream: OutputStream,
        startDate: Long,
        endDate: Long,
        patientName: String = "",
        patientDob: String = ""
    ) {
        val filtered = records
            .filter { it.timestamp in startDate..endDate }
            .sortedByDescending { it.timestamp }

        val document = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN

        val rangeFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Header
        canvas.drawText("Medical Blood Pressure Report", MARGIN, y + 20f, titlePaint)
        y += 35f

        if (patientName.isNotBlank()) {
            canvas.drawText("Patient: $patientName", MARGIN, y + 12f, headerPaint)
            y += 18f
        }
        if (patientDob.isNotBlank()) {
            canvas.drawText("DOB: $patientDob", MARGIN, y + 12f, bodyPaint)
            y += 18f
        }
        canvas.drawText(
            "Period: ${rangeFmt.format(Date(startDate))} - ${rangeFmt.format(Date(endDate))}",
            MARGIN, y + 12f, bodyPaint
        )
        y += 18f
        canvas.drawText("Total Readings: ${filtered.size}", MARGIN, y + 12f, bodyPaint)
        y += 25f

        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 15f

        if (filtered.isNotEmpty()) {
            // Overall averages
            val avgSys = filtered.map { it.systolic }.average().toInt()
            val avgDia = filtered.map { it.diastolic }.average().toInt()
            val avgHr = filtered.map { it.heartRate }.average().toInt()
            val maxSys = filtered.maxOf { it.systolic }
            val minSys = filtered.minOf { it.systolic }
            val maxDia = filtered.maxOf { it.diastolic }
            val minDia = filtered.minOf { it.diastolic }

            canvas.drawText("SUMMARY", MARGIN, y + 12f, headerPaint)
            y += 20f
            canvas.drawText("Average BP: $avgSys/$avgDia mmHg | Average HR: $avgHr bpm", MARGIN, y + 10f, bodyPaint)
            y += LINE_HEIGHT
            canvas.drawText("Systolic Range: $minSys - $maxSys mmHg | Diastolic Range: $minDia - $maxDia mmHg", MARGIN, y + 10f, bodyPaint)
            y += LINE_HEIGHT

            // Classification distribution
            val distribution = filtered.groupBy { BpClassifier.classify(it.systolic, it.diastolic) }
            y += 10f
            canvas.drawText("Classification Distribution:", MARGIN, y + 10f, headerPaint)
            y += LINE_HEIGHT + 4f
            BpCategory.entries.forEach { cat ->
                val count = distribution[cat]?.size ?: 0
                val pct = if (filtered.isNotEmpty()) (count * 100.0 / filtered.size).toInt() else 0
                val catPaint = Paint(bodyPaint).apply { color = categoryColor(cat) }
                canvas.drawText("  ${cat.label}: $count ($pct%)", MARGIN, y + 10f, catPaint)
                y += LINE_HEIGHT
            }

            // Morning vs Evening
            y += 10f
            val morningRecords = filtered.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.HOUR_OF_DAY) < 12
            }
            val eveningRecords = filtered.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.HOUR_OF_DAY) >= 12
            }

            canvas.drawText("TIME OF DAY ANALYSIS", MARGIN, y + 12f, headerPaint)
            y += 20f
            if (morningRecords.isNotEmpty()) {
                val mAvgSys = morningRecords.map { it.systolic }.average().toInt()
                val mAvgDia = morningRecords.map { it.diastolic }.average().toInt()
                canvas.drawText("  Morning (AM): Avg $mAvgSys/$mAvgDia mmHg (${morningRecords.size} readings)", MARGIN, y + 10f, bodyPaint)
                y += LINE_HEIGHT
            }
            if (eveningRecords.isNotEmpty()) {
                val eAvgSys = eveningRecords.map { it.systolic }.average().toInt()
                val eAvgDia = eveningRecords.map { it.diastolic }.average().toInt()
                canvas.drawText("  Evening (PM): Avg $eAvgSys/$eAvgDia mmHg (${eveningRecords.size} readings)", MARGIN, y + 10f, bodyPaint)
                y += LINE_HEIGHT
            }

            // Weight/SpO2 averages
            val weights = filtered.mapNotNull { it.weight }
            val spo2s = filtered.mapNotNull { it.spO2 }
            if (weights.isNotEmpty() || spo2s.isNotEmpty()) {
                y += 10f
                canvas.drawText("ADDITIONAL VITALS", MARGIN, y + 12f, headerPaint)
                y += 20f
                if (weights.isNotEmpty()) {
                    canvas.drawText("  Avg Weight: ${"%.1f".format(weights.average())} kg", MARGIN, y + 10f, bodyPaint)
                    y += LINE_HEIGHT
                }
                if (spo2s.isNotEmpty()) {
                    canvas.drawText("  Avg SpO2: ${spo2s.average().toInt()}%", MARGIN, y + 10f, bodyPaint)
                    y += LINE_HEIGHT
                }
            }
        }

        // Active medications
        val activeMeds = medications.filter { it.isActive }
        if (activeMeds.isNotEmpty()) {
            y += 15f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 15f
            canvas.drawText("CURRENT MEDICATIONS", MARGIN, y + 12f, headerPaint)
            y += 20f
            activeMeds.forEach { med ->
                canvas.drawText("  ${med.name} - ${med.dosage} (${Medication.frequencyLabel(med.frequency)})", MARGIN, y + 10f, bodyPaint)
                y += LINE_HEIGHT
            }
        }

        // All readings table on new page(s)
        if (filtered.isNotEmpty()) {
            document.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN

            canvas.drawText("ALL READINGS", MARGIN, y + 14f, headerPaint)
            y += 25f

            val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            drawTableHeader(canvas, y)
            y += LINE_HEIGHT + 4f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 6f

            for (record in filtered) {
                if (y > PAGE_HEIGHT - MARGIN - 20f) {
                    document.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = MARGIN
                    drawTableHeader(canvas, y)
                    y += LINE_HEIGHT + 4f
                    canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
                    y += 6f
                }

                val category = BpClassifier.classify(record.systolic, record.diastolic)
                val catPaint = Paint(bodyPaint).apply { color = categoryColor(category) }

                canvas.drawText(dateFormat.format(Date(record.timestamp)), MARGIN, y + 10f, bodyPaint)
                canvas.drawText("${record.systolic}/${record.diastolic}", 200f, y + 10f, bodyPaint)
                canvas.drawText("${record.heartRate}", 280f, y + 10f, bodyPaint)
                canvas.drawText(record.weight?.let { "%.1f".format(it) } ?: "-", 320f, y + 10f, bodyPaint)
                canvas.drawText(record.spO2?.toString() ?: "-", 365f, y + 10f, bodyPaint)
                canvas.drawText(category.label, 400f, y + 10f, catPaint)
                y += LINE_HEIGHT + 2f
            }
        }

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    private fun drawTableHeader(canvas: Canvas, y: Float) {
        canvas.drawText("Date/Time", MARGIN, y + 10f, headerPaint)
        canvas.drawText("BP", 200f, y + 10f, headerPaint)
        canvas.drawText("HR", 280f, y + 10f, headerPaint)
        canvas.drawText("Wt", 320f, y + 10f, headerPaint)
        canvas.drawText("SpO2", 365f, y + 10f, headerPaint)
        canvas.drawText("Classification", 400f, y + 10f, headerPaint)
    }

    private fun categoryColor(category: BpCategory): Int {
        return when (category) {
            BpCategory.NORMAL -> Color.parseColor("#388E3C")
            BpCategory.ELEVATED -> Color.parseColor("#FBC02D")
            BpCategory.HIGH_STAGE_1 -> Color.parseColor("#E65100")
            BpCategory.HIGH_STAGE_2 -> Color.parseColor("#D32F2F")
            BpCategory.CRISIS -> Color.parseColor("#880E4F")
        }
    }
}
