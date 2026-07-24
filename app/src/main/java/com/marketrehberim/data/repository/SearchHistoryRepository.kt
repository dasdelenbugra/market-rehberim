package com.marketrehberim.data.repository

import com.marketrehberim.data.local.dao.SearchHistoryDao
import com.marketrehberim.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SearchHistoryRepository @Inject constructor(
    private val dao: SearchHistoryDao,
) {
    val recent: Flow<List<String>> =
        dao.observeRecent().map { list -> list.map { it.query } }

    suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) dao.insert(SearchHistoryEntity(trimmed))
    }

    suspend fun clear() = dao.clear()
}
