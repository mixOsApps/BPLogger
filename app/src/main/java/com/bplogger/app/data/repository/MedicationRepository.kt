package com.bplogger.app.data.repository

import com.bplogger.app.data.db.Medication
import com.bplogger.app.data.db.MedicationDao
import kotlinx.coroutines.flow.Flow

class MedicationRepository(private val dao: MedicationDao) {
    fun getAll(): Flow<List<Medication>> = dao.getAll()

    fun getActive(): Flow<List<Medication>> = dao.getActive()

    suspend fun getActiveAt(timestamp: Long): List<Medication> = dao.getActiveAt(timestamp)

    suspend fun getAllList(): List<Medication> = dao.getAllList()

    suspend fun insert(medication: Medication) = dao.insert(medication)

    suspend fun insertAll(medications: List<Medication>) = dao.insertAll(medications)

    suspend fun update(medication: Medication) = dao.update(medication)

    suspend fun delete(medication: Medication) = dao.delete(medication)

    suspend fun deleteById(id: String) = dao.deleteById(id)
}
