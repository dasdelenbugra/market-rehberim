package com.marketrehberim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.marketrehberim.data.local.entity.SavingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingDao {
    /** Verilen andan bu yana toplam kazanç. Kayıt yoksa 0 döner. */
    @Query(
        "SELECT COALESCE(SUM(highestPrice - chosenPrice), 0) FROM savings " +
            "WHERE savedAt >= :since"
    )
    fun observeTotalSince(since: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM savings WHERE savedAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(entity: SavingEntity)

    @Query("DELETE FROM savings")
    suspend fun clear()
}
