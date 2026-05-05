package com.bplogger.app.data.repository

import com.bplogger.app.data.db.BpDao
import com.bplogger.app.data.db.BpRecord
import kotlinx.coroutines.flow.Flow

class BpRepository(private val dao: BpDao) {
    fun getAllRecords(): Flow<List<BpRecord>> = dao.getAllRecords()

    fun getRecordsByRange(startMs: Long, endMs: Long): Flow<List<BpRecord>> =
        dao.getRecordsByRange(startMs, endMs)

    suspend fun getUnsyncedRecords(): List<BpRecord> = dao.getUnsyncedRecords()

    suspend fun getAllRecordsList(): List<BpRecord> = dao.getAllRecordsList()

    suspend fun getLatestRecord(): BpRecord? = dao.getLatestRecord()

    suspend fun getLatestRecords(count: Int): List<BpRecord> = dao.getLatestRecords(count)

    suspend fun insert(record: BpRecord) = dao.insert(record)

    suspend fun insertAll(records: List<BpRecord>) = dao.insertAll(records)

    suspend fun update(record: BpRecord) = dao.update(record)

    suspend fun delete(record: BpRecord) = dao.delete(record)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun markAsSynced(ids: List<String>, syncTime: Long) =
        dao.markAsSynced(ids, syncTime)

    suspend fun getCount(): Int = dao.getCount()
}
