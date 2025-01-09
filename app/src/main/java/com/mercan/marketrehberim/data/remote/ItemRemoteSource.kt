package com.mercan.marketrehberim.data.remote

import com.mercan.marketrehberim.data.model.Item
import javax.inject.Inject

class ItemRemoteSource @Inject constructor() {
    suspend fun fetchItems(itemName: String): List<Item> {
        return emptyList()
    }
}