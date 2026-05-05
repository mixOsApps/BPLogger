package com.bplogger.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BpDao {

    @Query("SELECT * FROM bp_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<BpRecord>>

    @Query("SELECT * FROM bp_records WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC")
    fun getRecordsByRange(startMs: Long, endMs: Long): Flow<List<BpRecord>>

    @Query("SELECT * FROM bp_records WHERE syncedAt IS NULL ORDER BY timestamp DESC")
    suspend fun getUnsyncedRecords(): List<BpRecord>

    @Query("SELECT * FROM bp_records ORDER BY timestamp DESC")
    suspend fun getAllRecordsList(): List<BpRecord>

    @Query("SELECT * FROM bp_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): BpRecord?

    @Query("SELECT * FROM bp_records ORDER BY timestamp DESC LIMIT :count")
    suspend fun getLatestRecords(count: Int): List<BpRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BpRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<BpRecord>)

    @Update
    suspend fun update(record: BpRecord)

    @Delete
    suspend fun delete(record: BpRecord)

    @Query("DELETE FROM bp_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE bp_records SET syncedAt = :syncTime WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>, syncTime: Long)

    @Query("SELECT COUNT(*) FROM bp_records")
    suspend fun getCount(): Int
}
