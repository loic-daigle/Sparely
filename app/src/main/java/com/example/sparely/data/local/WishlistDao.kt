package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlist(wishlist: WishlistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlists(wishlists: List<WishlistEntity>)

    @Delete
    suspend fun deleteWishlist(wishlist: WishlistEntity)

    @Query("DELETE FROM wishlists WHERE id = :wishlistId")
    suspend fun deleteWishlistById(wishlistId: Long)

    @Query("SELECT * FROM wishlists WHERE id = :wishlistId LIMIT 1")
    suspend fun getWishlistById(wishlistId: Long): WishlistEntity?

    @Query("SELECT * FROM wishlists WHERE archived = 0 ORDER BY priority DESC, createdAt DESC")
    fun observeActiveWishlists(): Flow<List<WishlistEntity>>

    @Query("SELECT * FROM wishlists WHERE archived = 0 ORDER BY priority DESC, createdAt DESC")
    suspend fun getActiveWishlists(): List<WishlistEntity>

    @Query("SELECT * FROM wishlists ORDER BY archived ASC, priority DESC, createdAt DESC")
    suspend fun getAllWishlists(): List<WishlistEntity>

    @Query("SELECT COUNT(*) FROM wishlists WHERE archived = 0")
    suspend fun countActiveWishlists(): Int

    @Query("UPDATE wishlists SET currentSavings = :amount WHERE id = :wishlistId")
    suspend fun updateWishlistSavings(wishlistId: Long, amount: Double)

    @Query("UPDATE wishlists SET currentSavings = currentSavings + :delta WHERE id = :wishlistId")
    suspend fun incrementWishlistSavings(wishlistId: Long, delta: Double)

    @Query("UPDATE wishlists SET archived = :archived WHERE id = :wishlistId")
    suspend fun updateWishlistArchived(wishlistId: Long, archived: Boolean)

    @Query("UPDATE wishlists SET cooldownExpiresAt = :expiryTime WHERE id = :wishlistId")
    suspend fun updateCooldownExpiry(wishlistId: Long, expiryTime: java.time.LocalDateTime)

    @Query("SELECT * FROM wishlists WHERE cooldownExpiresAt IS NOT NULL AND cooldownExpiresAt <= :now AND archived = 0")
    suspend fun getExpiredCooldownWishlists(now: java.time.LocalDateTime): List<WishlistEntity>

    @Query("DELETE FROM wishlists")
    suspend fun clearAll()
}

@Dao
interface WishlistSavingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavings(savings: WishlistSavingsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsList(savingsList: List<WishlistSavingsEntity>)

    @Delete
    suspend fun deleteSavings(savings: WishlistSavingsEntity)

    @Query("SELECT * FROM wishlist_savings WHERE wishlistId = :wishlistId ORDER BY date DESC")
    suspend fun getSavingsForWishlist(wishlistId: Long): List<WishlistSavingsEntity>

    @Query("SELECT * FROM wishlist_savings WHERE wishlistId = :wishlistId ORDER BY date DESC")
    fun observeSavingsForWishlist(wishlistId: Long): Flow<List<WishlistSavingsEntity>>

    @Query("SELECT IFNULL(SUM(amount), 0.0) FROM wishlist_savings WHERE wishlistId = :wishlistId")
    suspend fun getTotalSavingsForWishlist(wishlistId: Long): Double

    @Query("DELETE FROM wishlist_savings WHERE wishlistId = :wishlistId")
    suspend fun deleteSavingsForWishlist(wishlistId: Long)

    @Query("SELECT * FROM wishlist_savings ORDER BY date DESC")
    suspend fun getAllSavings(): List<WishlistSavingsEntity>

    @Query("DELETE FROM wishlist_savings")
    suspend fun clearAll()
}
