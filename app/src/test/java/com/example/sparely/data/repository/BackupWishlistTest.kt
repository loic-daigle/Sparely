package com.example.sparely.data.repository

import com.example.sparely.data.local.WishlistSavingsEntity
import com.example.sparely.data.utils.InstantAdapter
import com.example.sparely.data.utils.LocalDateAdapter
import com.example.sparely.data.utils.LocalDateTimeAdapter
import com.example.sparely.data.utils.YearMonthAdapter
import com.example.sparely.domain.model.BackupData
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.Wishlist
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** The backup file is plain JSON; these check that wishlists survive a trip through it. */
class BackupWishlistTest {

    // Same settings the app's BackupRepository uses.
    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeHierarchyAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeHierarchyAdapter(Instant::class.java, InstantAdapter())
        .registerTypeHierarchyAdapter(YearMonth::class.java, YearMonthAdapter())
        .serializeNulls()
        .create()

    private val created = LocalDateTime.of(2026, 10, 7, 12, 30)

    private val wish = Wishlist(
        id = 7,
        description = "Headphones",
        targetAmount = 349.99,
        currentSavings = 120.0,
        priority = 3,
        createdAt = created,
        cooldownExpiresAt = created.plusDays(5),
        targetDate = LocalDate.of(2027, 1, 15),
        isReallyNeeded = false,
        archived = false
    )

    private fun roundTrip(backup: BackupData): BackupData =
        gson.fromJson(gson.toJson(backup), BackupData::class.java)

    @Test
    fun `a wish keeps every field including its target date and reflection time`() {
        val restored = roundTrip(BackupData(settings = SparelySettings(), wishlists = listOf(wish))).wishlists!!.single()

        assertEquals(wish, restored)
    }

    @Test
    fun `savings recorded for a wish survive`() {
        val saving = WishlistSavingsEntity(id = 1, wishlistId = 7, amount = 40.0, date = created, source = "MANUAL_ALLOCATION")
        val restored = roundTrip(
            BackupData(settings = SparelySettings(), wishlists = listOf(wish), wishlistSavings = listOf(saving))
        ).wishlistSavings!!.single()

        assertEquals(saving, restored)
    }

    @Test
    fun `a wish without a target date or reflection time stays that way`() {
        val plain = wish.copy(targetDate = null, cooldownExpiresAt = null)
        val restored = roundTrip(BackupData(settings = SparelySettings(), wishlists = listOf(plain))).wishlists!!.single()

        assertNull(restored.targetDate)
        assertNull(restored.cooldownExpiresAt)
    }

    @Test
    fun `a backup made before target dates existed still loads`() {
        val oldWish = """
            {"id":3,"description":"Bike","targetAmount":500.0,"currentSavings":50.0,"priority":1,
             "createdAt":"${created}","isReallyNeeded":true,"archived":false}
        """.trimIndent()
        val json = gson.toJson(BackupData(settings = SparelySettings())).replace("\"wishlists\":null", "\"wishlists\":[$oldWish]")

        val restored = gson.fromJson(json, BackupData::class.java).wishlists!!.single()

        assertEquals("Bike", restored.description)
        assertNull(restored.targetDate)
    }

    @Test
    fun `archived wishes are part of the backup`() {
        val restored = roundTrip(
            BackupData(settings = SparelySettings(), wishlists = listOf(wish.copy(archived = true)))
        ).wishlists!!.single()

        assertEquals(true, restored.archived)
    }
}
