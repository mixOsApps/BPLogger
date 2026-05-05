package com.bplogger.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val frequency: String,  // "daily", "twice_daily", "weekly", "as_needed"
    val startDate: Long,
    val endDate: Long? = null,
    val notes: String = "",
    val isActive: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("dosage", dosage)
            put("frequency", frequency)
            put("startDate", startDate)
            if (endDate != null) put("endDate", endDate)
            put("notes", notes)
            put("isActive", isActive)
        }
    }

    companion object {
        val FREQUENCIES = listOf(
            "daily" to "Daily",
            "twice_daily" to "Twice Daily",
            "weekly" to "Weekly",
            "as_needed" to "As Needed"
        )

        fun frequencyLabel(code: String): String {
            return FREQUENCIES.find { it.first == code }?.second ?: code
        }
    }
}
