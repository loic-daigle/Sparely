package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Transaction
    @Query("SELECT * FROM savings_challenges ORDER BY startDate DESC")
    fun observeChallenges(): Flow<List<SavingsChallengeWithMilestones>>

    // Upsert, not REPLACE: REPLACE deletes the row first, and the cascade took what is attached to it
    @Upsert
    suspend fun upsertChallenge(entity: SavingsChallengeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMilestones(milestones: List<ChallengeMilestoneEntity>)

    @Query("DELETE FROM challenge_milestones WHERE challengeId = :challengeId")
    suspend fun deleteMilestonesForChallenge(challengeId: Long)

    @Query("DELETE FROM savings_challenges WHERE id = :id")
    suspend fun deleteChallengeById(id: Long)

    @Query("DELETE FROM savings_challenges")
    suspend fun deleteAllChallenges()

    @Query("DELETE FROM challenge_milestones")
    suspend fun deleteAllMilestones()
}

data class SavingsChallengeWithMilestones(
    @Embedded val challenge: SavingsChallengeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "challengeId"
    )
    val milestones: List<ChallengeMilestoneEntity>
)
