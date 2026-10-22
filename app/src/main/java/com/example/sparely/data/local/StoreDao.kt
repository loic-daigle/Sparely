package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT id, name, iconName, createdAt, websiteUrl FROM stores ORDER BY name ASC")
    fun observeStores(): Flow<List<StoreEntity>>

    @Query("SELECT id, name, iconName, createdAt, websiteUrl FROM stores WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT 20")
    suspend fun searchStores(query: String): List<StoreEntity>

    @Query("SELECT id, name, iconName, createdAt, websiteUrl FROM stores WHERE id = :id LIMIT 1")
    suspend fun getStoreById(id: Long): StoreEntity?

    @Query("SELECT id, name, iconName, createdAt, websiteUrl FROM stores WHERE name = :name LIMIT 1")
    suspend fun getStoreByName(name: String): StoreEntity?

    // Upsert, not REPLACE: REPLACE deletes the row first, and the cascade took what is attached to it
    @Upsert
    suspend fun insertStore(store: StoreEntity): Long

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Delete
    suspend fun deleteStore(store: StoreEntity)

    @Query("DELETE FROM stores")
    suspend fun clearAll()
}
