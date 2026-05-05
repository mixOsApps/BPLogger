package com.bplogger.app.util

import android.util.Log
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

private const val TAG = "OcrParser"

data class OcrBpRow(
    val date: String,       // "MMdd" e.g. "0130"
    val time: String,       // "HHmm" e.g. "0700"
    val systolic: String,
    val diastolic: String,
    val pulse: String
)

/**
 * Parses ML Kit Vision Text result into BP records.
 *
 * ML Kit reads spreadsheet/tabular data column-by-column.
 * This parser uses bounding box Y-coordinates to reconstruct rows,
 * then classifies each element into date, time, BP reading, or pulse
 * based on its X-coordinate (column position).
 */
fun parseOcrVisionText(visionText: Text): List<OcrBpRow> {
    // Step 1: Extract all text elements with their bounding box centers
    data class TextElement(
        val text: String,
        val centerX: Float,
        val centerY: Float,
        val top: Int,
        val bottom: Int
    )

    val elements = mutableListOf<TextElement>()

    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            for (element in line.elements) {
                val box = element.boundingBox ?: continue
                val cleanText = element.text.trim()
                if (cleanText.isBlank()) continue

                elements.add(
                    TextElement(
                        text = cleanText,
                        centerX = (box.left + box.right) / 2f,
                        centerY = (box.top + box.bottom) / 2f,
                        top = box.top,
                        bottom = box.bottom
                    )
                )
                Log.d(TAG, "Element: '${cleanText}' at X=${box.left}-${box.right}, Y=${box.top}-${box.bottom}")
            }
        }
    }

    if (elements.isEmpty()) {
        Log.w(TAG, "No text elements found")
        return emptyList()
    }

    // Step 2: Group elements into rows by Y-coordinate proximity
    val sortedByY = elements.sortedBy { it.centerY }
    val rows = mutableListOf<MutableList<TextElement>>()
    val rowThreshold = sortedByY.let {
        // Estimate row height as average element height
        val avgHeight = it.map { e -> e.bottom - e.top }.average()
        (avgHeight * 0.6).coerceAtLeast(15.0)
    }

    for (element in sortedByY) {
        // Find existing row that this element belongs to
        val matchingRow = rows.find { row ->
            row.any { abs(it.centerY - element.centerY) < rowThreshold }
        }
        if (matchingRow != null) {
            matchingRow.add(element)
        } else {
            rows.add(mutableListOf(element))
        }
    }

    // Sort elements within each row by X-coordinate (left to right)
    rows.forEach { row -> row.sortBy { it.centerX } }

    Log.d(TAG, "Grouped into ${rows.size} rows")
    rows.forEachIndexed { i, row ->
        Log.d(TAG, "Row $i: ${row.map { "'${it.text}'" }}")
    }

    // Step 3: Parse each row into an OcrBpRow
    val results = mutableListOf<OcrBpRow>()
    var lastDate = ""

    for (row in rows) {
        try {
            val parsed = parseRow(row.map { it.text })
            if (parsed != null) {
                if (parsed.date.isNotBlank()) {
                    lastDate = parsed.date
                }
                val finalRow = if (parsed.date.isBlank() && lastDate.isNotBlank()) {
                    parsed.copy(date = lastDate)
                } else {
                    parsed
                }
                if (finalRow.date.isNotBlank() && finalRow.time.isNotBlank() &&
                    finalRow.systolic.isNotBlank() && finalRow.diastolic.isNotBlank()
                ) {
                    results.add(finalRow)
                    Log.d(TAG, "Parsed row: $finalRow")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse row: ${row.map { it.text }}", e)
        }
    }

    return results
}

/**
 * Classifies tokens in a single row into date, time, BP, pulse.
 *
 * Token classification rules:
 * - Contains "/" → BP reading (systolic/diastolic)
 * - 3-4 digit number that looks like a valid time (hour 0-23) → time
 * - 3-4 digit number that looks like a valid date (month 01-12, day 01-31) → date
 * - 1-3 digit number → pulse
 */
private fun parseRow(tokens: List<String>): OcrBpRow? {
    if (tokens.isEmpty()) return null

    var date = ""
    var time = ""
    var systolic = ""
    var diastolic = ""
    var pulse = ""

    // Track which tokens are consumed
    val consumed = mutableSetOf<Int>()

    // Pass 1: Find BP reading (contains "/")
    for ((i, token) in tokens.withIndex()) {
        if (token.contains("/")) {
            val parts = token.split("/")
            if (parts.size == 2) {
                val sys = parts[0].filter { it.isDigit() }
                val dia = parts[1].filter { it.isDigit() }
                if (sys.isNotBlank() && dia.isNotBlank()) {
                    systolic = sys
                    diastolic = dia
                    consumed.add(i)
                    break
                }
            }
        }
    }

    // Pass 2: Classify remaining tokens (left to right = date, time, ..., pulse)
    val remaining = tokens.filterIndexed { i, _ -> i !in consumed }
    val numericTokens = remaining.mapNotNull { token ->
        // Fix common OCR misreads: O→0, l→1, I→1, S→5, B→8
        val cleaned = token
            .replace('O', '0')
            .replace('o', '0')
            .replace('l', '1')
            .replace('I', '1')
            .filter { it.isDigit() }
        if (cleaned.isNotBlank()) cleaned else null
    }

    // Classify numeric tokens by position and value
    // In order: first plausible date-like → date, first plausible time-like → time, rest → pulse
    val unclassified = numericTokens.toMutableList()

    // Try to find date (MMdd: 4 digits, month 01-12, day 01-31)
    val dateCandidate = unclassified.firstOrNull { tok ->
        if (tok.length in 3..4) {
            val padded = tok.padStart(4, '0')
            val month = padded.substring(0, 2).toIntOrNull() ?: 0
            val day = padded.substring(2, 4).toIntOrNull() ?: 0
            month in 1..12 && day in 1..31
        } else false
    }
    if (dateCandidate != null) {
        date = dateCandidate.padStart(4, '0')
        unclassified.remove(dateCandidate)
    }

    // Try to find time (HHmm: 4 digits, hour 0-23, minute 0-59)
    val timeCandidate = unclassified.firstOrNull { tok ->
        if (tok.length in 3..4) {
            val padded = tok.padStart(4, '0')
            val hour = padded.substring(0, 2).toIntOrNull() ?: -1
            val minute = padded.substring(2, 4).toIntOrNull() ?: -1
            hour in 0..23 && minute in 0..59
        } else false
    }
    if (timeCandidate != null) {
        time = timeCandidate.padStart(4, '0')
        unclassified.remove(timeCandidate)
    }

    // Remaining 1-3 digit numbers → pulse (take first one)
    if (pulse.isBlank() && unclassified.isNotEmpty()) {
        val pulseCandidate = unclassified.firstOrNull { it.length in 1..3 }
        if (pulseCandidate != null) {
            pulse = pulseCandidate
        }
    }

    // If we have no BP reading at all, skip
    if (systolic.isBlank() && diastolic.isBlank()) return null

    return OcrBpRow(
        date = date,
        time = time,
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse
    )
}

/**
 * Fallback: parse from raw text string (for testing or simple formats).
 */
fun parseOcrText(rawText: String): List<OcrBpRow> {
    val results = mutableListOf<OcrBpRow>()
    var lastDate = ""

    val lines = rawText.lines().filter { it.isNotBlank() }

    for (line in lines) {
        try {
            val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val parsed = parseRow(tokens)
            if (parsed != null) {
                if (parsed.date.isNotBlank()) lastDate = parsed.date
                val finalRow = if (parsed.date.isBlank() && lastDate.isNotBlank()) {
                    parsed.copy(date = lastDate)
                } else parsed
                if (finalRow.date.isNotBlank() && finalRow.time.isNotBlank() &&
                    finalRow.systolic.isNotBlank() && finalRow.diastolic.isNotBlank()
                ) {
                    results.add(finalRow)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse line: $line", e)
        }
    }

    return results
}