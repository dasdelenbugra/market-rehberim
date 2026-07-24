package com.marketrehberim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.marketrehberim.data.local.dao.FavoriteDao
import com.marketrehberim.data.local.dao.SavingDao
import com.marketrehberim.data.local.dao.SearchHistoryDao
import com.marketrehberim.data.local.entity.FavoriteEntity
import com.marketrehberim.data.local.entity.SavingEntity
import com.marketrehberim.data.local.entity.SearchHistoryEntity

@Database(
    entities = [FavoriteEntity::class, SearchHistoryEntity::class, SavingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun savingDao(): SavingDao
}
