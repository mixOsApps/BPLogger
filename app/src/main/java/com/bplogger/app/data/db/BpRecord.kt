package com.bplogger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "bp_records")
data class BpRecord(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val systolic: Int,
    val diastolic: Int,
    val heartRate: Int,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val source: String = "manual", // "manual" | "ocr" | "import"
    val weight: Float? = null,     // kg
    val spO2: Int? = null          // percentage (0-100)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("systolic", systolic)
            put("diastolic", diastolic)
            put("heartRate", heartRate)
            put("notes", notes)
            put("timestamp", timestamp)
            put("source", source)
            if (weight != null) put("weight", weight.toDouble())
            if (spO2 != null) put("spO2", spO2)
        }
    }
}
