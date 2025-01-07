package com.mercan.marketrehberim.data.repository

import com.mercan.marketrehberim.data.model.Item
import com.mercan.marketrehberim.data.remote.ItemRemoteSource
import javax.inject.Inject

class ItemRepository @Inject constructor(
    private val itemRemoteSource: ItemRemoteSource,
) {
    suspend fun getItems(name: String): List<Item> {
        return itemRemoteSource.fetchItems(name)
    }
}