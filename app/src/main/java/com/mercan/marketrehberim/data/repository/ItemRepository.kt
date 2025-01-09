package com.mercan.marketrehberim.data.repository

import com.mercan.marketrehberim.data.model.Item
import com.mercan.marketrehberim.data.remote.ItemRemoteSource
import javax.inject.Inject

class ItemRepository @Inject constructor(
    private val itemRemoteSource: ItemRemoteSource,
) {
    suspend fun getItems(name: String): List<Item> {
        val migrosItems = itemRemoteSource.fetchMigros(name).toMutableList()
        val a101Items = itemRemoteSource.fetchA101(name).toMutableList()
        val erenlerItems = itemRemoteSource.fetchErenler(name).toMutableList()

        val returnList = mutableListOf<Item>().apply {
            addAll(migrosItems)
            addAll(a101Items)
            addAll(erenlerItems)
        }

        return returnList.sortedBy { it.price.toDouble() }
    }
}