package com.marketrehberim.data.repository

import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.ItemRemoteSource
import com.marketrehberim.data.remote.dto.BasketRequest
import com.marketrehberim.data.remote.dto.BasketResponse
import com.marketrehberim.data.remote.dto.CityDto
import com.marketrehberim.data.remote.dto.HistoryPoint
import com.marketrehberim.data.remote.dto.MarketsDto
import com.marketrehberim.data.remote.dto.PriceSubmission
import javax.inject.Inject

/**
 * Uzak veri kaynağına erişim. Backend birleşik aramayı (ulusal + crowdsourced)
 * tek uçta topladığından istemci tarafında ayrı market çağrılarına gerek yoktur.
 * Ağ hataları yukarı sızmaz; boş sonuç döner.
 */
class ItemRepository @Inject constructor(
    private val remote: ItemRemoteSource,
) {
    suspend fun search(city: String, name: String): List<Item> =
        runCatching { remote.search(city, name) }.getOrDefault(emptyList())

    suspend fun cities(): List<CityDto> =
        runCatching { remote.cities() }.getOrDefault(emptyList())

    suspend fun markets(city: String): MarketsDto =
        runCatching { remote.markets(city) }.getOrDefault(MarketsDto())

    suspend fun history(market: String, name: String): List<HistoryPoint> =
        runCatching { remote.history(market, name) }.getOrDefault(emptyList())

    suspend fun optimizeBasket(city: String, items: List<String>): BasketResponse? =
        runCatching { remote.optimizeBasket(BasketRequest(city, items)) }.getOrNull()

    suspend fun submitPrice(submission: PriceSubmission): Boolean =
        runCatching { remote.submitPrice(submission).isSuccessful }.getOrDefault(false)
}
