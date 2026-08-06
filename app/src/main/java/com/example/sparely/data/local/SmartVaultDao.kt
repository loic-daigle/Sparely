package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface SmartVaultDao {
    @Transaction
    @Query("SELECT * FROM smart_vaults WHERE archived = 0 ORDER BY CASE priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END, targetDate IS NULL, targetDate ASC")
    fun observeActiveVaults(): Flow<List<SmartVaultWithSchedules>>

    @Transaction
    @Query("SELECT * FROM smart_vaults ORDER BY archived ASC, name ASC")
    fun observeAllVaults(): Flow<List<SmartVaultWithSchedules>>

    @Query("SELECT * FROM smart_vaults WHERE id = :id")
    suspend fun getVaultById(id: Long): SmartVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVault(entity: SmartVaultEntity): Long

    @Query("DELETE FROM smart_vaults")
    suspend fun clearAllVaults()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContribution(entity: VaultContributionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(entity: VaultBalanceAdjustmentEntity): Long

    @Query("SELECT * FROM vault_contributions WHERE vaultId = :vaultId ORDER BY date DESC")
    suspend fun getContributionsForVault(vaultId: Long): List<VaultContributionEntity>

    @Query("SELECT * FROM vault_contributions WHERE vaultId = :vaultId AND reconciled = 1 ORDER BY date DESC")
    suspend fun getReconciledContributionsForVault(vaultId: Long): List<VaultContributionEntity>

    @Query("SELECT * FROM vault_balance_adjustments WHERE vaultId = :vaultId ORDER BY createdAt DESC")
    suspend fun getAdjustmentsForVault(vaultId: Long): List<VaultBalanceAdjustmentEntity>

    @Query("SELECT * FROM vault_contributions WHERE reconciled = 0 AND source = 'AUTO_DEPOSIT'")
    suspend fun getPendingAutoDeposits(): List<VaultContributionEntity>
    
    @Query("SELECT * FROM vault_contributions WHERE reconciled = 0 ORDER BY date DESC")
    suspend fun getPendingContributions(): List<VaultContributionEntity>

    @Query("SELECT * FROM vault_contributions WHERE reconciled = 0 ORDER BY date DESC")
    fun observePendingContributions(): Flow<List<VaultContributionEntity>>

    @Query("SELECT * FROM vault_contributions WHERE reconciled = 0 AND savingsAccountId IS NOT NULL ORDER BY date DESC")
    fun observePendingHisaContributions(): Flow<List<VaultContributionEntity>>

    @Query("SELECT * FROM vault_contributions WHERE savingsAccountId = :accountId ORDER BY date DESC")
    suspend fun getContributionsForSavingsAccount(accountId: Long): List<VaultContributionEntity>

    @Query("SELECT * FROM vault_contributions WHERE savingsAccountId = :accountId AND reconciled = 0 ORDER BY date DESC")
    suspend fun getPendingContributionsForSavingsAccount(accountId: Long): List<VaultContributionEntity>


    @Query("SELECT * FROM vault_contributions WHERE relatedExpenseId = :expenseId")
    suspend fun getContributionsForExpense(expenseId: Long): List<VaultContributionEntity>

    @Query("DELETE FROM vault_contributions WHERE relatedExpenseId = :expenseId AND reconciled = 0")
    suspend fun deletePendingContributionsForExpense(expenseId: Long)
    
    @Query("SELECT * FROM vault_contributions WHERE id = :id")
    suspend fun getContributionById(id: Long): VaultContributionEntity?

    @Query("UPDATE smart_vaults SET currentBalance = currentBalance + :delta, lastContributionDate = :date WHERE id = :vaultId")
    suspend fun incrementVaultBalance(vaultId: Long, delta: Double, date: LocalDate?)

    @Query("UPDATE smart_vaults SET currentBalance = :balance, lastContributionDate = :date WHERE id = :vaultId")
    suspend fun setVaultBalance(vaultId: Long, balance: Double, date: LocalDate?)

    @Query(
        "UPDATE smart_vaults SET archived = :archived WHERE id = :vaultId"
    )
    suspend fun updateVaultArchived(vaultId: Long, archived: Boolean)

    @Query("DELETE FROM smart_vaults WHERE id = :vaultId")
    suspend fun deleteVault(vaultId: Long)

    @Query("UPDATE vault_contributions SET reconciled = 1 WHERE id = :contributionId")
    suspend fun markContributionReconciled(contributionId: Long)

    @Query("DELETE FROM vault_contributions WHERE id = :id")
    suspend fun deleteContribution(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(entity: VaultScheduleEntity): Long

    @Update
    suspend fun updateSchedule(entity: VaultScheduleEntity)

    @Delete
    suspend fun deleteSchedule(entity: VaultScheduleEntity)

    @Query("DELETE FROM vault_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("SELECT * FROM vault_schedules WHERE vaultId = :vaultId ORDER BY nextRunAt IS NULL, nextRunAt ASC")
    suspend fun getSchedulesForVault(vaultId: Long): List<VaultScheduleEntity>

    @Query("SELECT * FROM vault_schedules WHERE enabled = 1")
    suspend fun getEnabledSchedules(): List<VaultScheduleEntity>

    @Query("SELECT * FROM vault_schedules WHERE enabled = 1 AND (nextRunAt IS NULL OR nextRunAt <= :cutoff)")
    suspend fun getDueSchedules(cutoff: LocalDateTime): List<VaultScheduleEntity>

    @Query("DELETE FROM vault_contributions")
    suspend fun clearAllContributions()

    @Query("DELETE FROM vault_balance_adjustments")
    suspend fun clearAllAdjustments()

    @Query("DELETE FROM vault_schedules")
    suspend fun clearAllSchedules()

    @Query("SELECT * FROM vault_contributions")
    suspend fun getAllContributions(): List<VaultContributionEntity>

    @Query("SELECT * FROM vault_balance_adjustments")
    suspend fun getAllAdjustments(): List<VaultBalanceAdjustmentEntity>
}
