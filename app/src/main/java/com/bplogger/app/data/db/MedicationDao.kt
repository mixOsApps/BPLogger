package com.bplogger.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY isActive DESC, startDate DESC")
    fun getAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY startDate DESC")
    fun getActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE isActive = 1 AND startDate <= :timestamp AND (endDate IS NULL OR endDate >= :timestamp) ORDER BY name ASC")
    suspend fun getActiveAt(timestamp: Long): List<Medication>

    @Query("SELECT * FROM medications ORDER BY isActive DESC, startDate DESC")
    suspend fun getAllList(): List<Medication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<Medication>)

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: String)
}
