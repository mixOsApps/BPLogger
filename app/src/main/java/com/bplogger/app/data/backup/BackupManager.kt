package com.bplogger.app.data.backup

import android.content.Context
import android.net.Uri
import com.bplogger.app.data.db.BpRecord
import com.bplogger.app.data.db.Medication
import com.bplogger.app.data.repository.BpRepository
import com.bplogger.app.data.repository.MedicationRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupManager(
    private val bpRepository: BpRepository,
    private val medicationRepository: MedicationRepository
) {
    data class BackupResult(val recordCount: Int, val medicationCount: Int)
    data class RestoreResult(val newRecords: Int, val newMedications: Int, val skipped: Int)

    suspend fun exportBackup(context: Context, uri: Uri): BackupResult {
        val records = bpRepository.getAllRecordsList()
        val medications = medicationRepository.getAllList()

        val json = JSONObject().apply {
            put("version", 3)
            put("exportedAt", System.currentTimeMillis())
            put("appVersion", "BPLogger")

            val recordsArray = JSONArray()
            records.forEach { recordsArray.put(it.toJson()) }
            put("records", recordsArray)

            val medsArray = JSONArray()
            medications.forEach { medsArray.put(it.toJson()) }
            put("medications", medsArray)
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toString(2).toByteArray(Charsets.UTF_8))
        }

        return BackupResult(records.size, medications.size)
    }

    suspend fun importBackup(context: Context, uri: Uri): RestoreResult {
        val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
        } ?: throw Exception("Could not read file")

        val json = JSONObject(jsonString)

        // Parse records
        val recordsArray = json.optJSONArray("records") ?: JSONArray()
        val importedRecords = mutableListOf<BpRecord>()
        for (i in 0 until recordsArray.length()) {
            try {
                val obj = recordsArray.getJSONObject(i)
                importedRecords.add(
                    BpRecord(
                        id = obj.getString("id"),
                        systolic = obj.getInt("systolic"),
                        diastolic = obj.getInt("diastolic"),
                        heartRate = obj.getInt("heartRate"),
                        timestamp = obj.getLong("timestamp"),
                        notes = obj.optString("notes", ""),
                        source = obj.optString("source", "manual"),
                        weight = if (obj.has("weight")) obj.getDouble("weight").toFloat() else null,
                        spO2 = if (obj.has("spO2")) obj.getInt("spO2") else null
                    )
                )
            } catch (_: Exception) { }
        }

        // Parse medications
        val medsArray = json.optJSONArray("medications") ?: JSONArray()
        val importedMeds = mutableListOf<Medication>()
        for (i in 0 until medsArray.length()) {
            try {
                val obj = medsArray.getJSONObject(i)
                importedMeds.add(
                    Medication(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        dosage = obj.getString("dosage"),
                        frequency = obj.getString("frequency"),
                        startDate = obj.getLong("startDate"),
                        endDate = if (obj.has("endDate")) obj.getLong("endDate") else null,
                        notes = obj.optString("notes", ""),
                        isActive = obj.optBoolean("isActive", true)
                    )
                )
            } catch (_: Exception) { }
        }

        // Get existing IDs to skip duplicates
        val existingRecordIds = bpRepository.getAllRecordsList().map { it.id }.toSet()
        val existingMedIds = medicationRepository.getAllList().map { it.id }.toSet()

        val newRecords = importedRecords.filter { it.id !in existingRecordIds }
        val newMeds = importedMeds.filter { it.id !in existingMedIds }
        val skipped = (importedRecords.size - newRecords.size) + (importedMeds.size - newMeds.size)

        if (newRecords.isNotEmpty()) bpRepository.insertAll(newRecords)
        if (newMeds.isNotEmpty()) medicationRepository.insertAll(newMeds)

        return RestoreResult(newRecords.size, newMeds.size, skipped)
    }
}
