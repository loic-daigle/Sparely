package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<AssetEntity>)

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :assetId")
    suspend fun deleteAssetById(assetId: Long)

    @Query("SELECT * FROM assets WHERE id = :assetId LIMIT 1")
    suspend fun getAssetById(assetId: Long): AssetEntity?

    @Query("SELECT * FROM assets WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeActiveAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE archived = 0 ORDER BY createdAt DESC")
    suspend fun getActiveAssets(): List<AssetEntity>

    @Query("SELECT * FROM assets ORDER BY archived ASC, createdAt DESC")
    suspend fun getAllAssets(): List<AssetEntity>

    @Query("UPDATE assets SET archived = :archived WHERE id = :assetId")
    suspend fun updateAssetArchived(assetId: Long, archived: Boolean)

    @Query("DELETE FROM assets")
    suspend fun clearAll()
}

@Dao
interface AssetExpenseLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: AssetExpenseLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<AssetExpenseLinkEntity>)

    @Delete
    suspend fun deleteLink(link: AssetExpenseLinkEntity)

    @Query("DELETE FROM asset_expense_links WHERE id = :linkId")
    suspend fun deleteLinkById(linkId: Long)

    @Query("SELECT * FROM asset_expense_links WHERE assetId = :assetId ORDER BY linkedAt DESC")
    suspend fun getLinksForAsset(assetId: Long): List<AssetExpenseLinkEntity>

    @Query("SELECT * FROM asset_expense_links WHERE expenseId = :expenseId ORDER BY linkedAt DESC")
    suspend fun getLinksForExpense(expenseId: Long): List<AssetExpenseLinkEntity>

    @Query("DELETE FROM asset_expense_links WHERE assetId = :assetId AND expenseId = :expenseId")
    suspend fun deleteLink(assetId: Long, expenseId: Long)

    @Query("SELECT IFNULL(SUM(CASE WHEN e.amount > 0 THEN e.amount * (l.percentageAllocated / 100.0) ELSE 0 END), 0.0) FROM asset_expense_links l JOIN expenses e ON l.expenseId = e.id WHERE l.assetId = :assetId")
    suspend fun getTotalSpendingForAsset(assetId: Long): Double

    @Query("DELETE FROM asset_expense_links WHERE expenseId = :expenseId")
    suspend fun deleteLinksForExpense(expenseId: Long)

    @Query("DELETE FROM asset_expense_links WHERE assetId = :assetId")
    suspend fun deleteLinksForAsset(assetId: Long)

    @Query("SELECT * FROM asset_expense_links ORDER BY linkedAt DESC")
    suspend fun getAllLinks(): List<AssetExpenseLinkEntity>

    @Query("DELETE FROM asset_expense_links")
    suspend fun clearAll()
}
