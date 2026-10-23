package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface FrozenFundDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(frozen: FrozenFundEntity): Long

    @Query("SELECT * FROM frozen_funds ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FrozenFundEntity>>

    @Query("SELECT * FROM frozen_funds WHERE id = :id")
    suspend fun getById(id: Long): FrozenFundEntity?

    @Query("SELECT * FROM frozen_funds WHERE pendingType = :type AND pendingId = :pendingId")
    suspend fun findForPending(type: String, pendingId: Long): List<FrozenFundEntity>

    @Query("DELETE FROM frozen_funds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM frozen_funds WHERE pendingType = :type AND pendingId = :pendingId")
    suspend fun deleteForPending(type: String, pendingId: Long)

    @Query("SELECT * FROM frozen_funds")
    suspend fun getAll(): List<FrozenFundEntity>

    @Query("SELECT IFNULL(SUM(amount), 0.0) FROM frozen_funds")
    suspend fun totalFrozen(): Double

    @Query("DELETE FROM frozen_funds")
    suspend fun deleteAll()
}
