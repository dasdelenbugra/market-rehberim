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
 * Arama sonucu: fiyat listesi + ulusal fiyatların son güncellenme zamanı.
 *
 * @param updatedAt `X-Data-Updated` başlığından gelen ISO 8601 metni; kaynak
 *   bildirmezse (mock, ağ hatası, damga yok) `null`. "Son güncelleme" rozeti bunu
 *   kullanır.
 */
data class SearchResult(
    val items: List<Item> = emptyList(),
    val updatedAt: String? = null,
)

/**
 * Uzak veri kaynağına erişim. Backend birleşik aramayı (ulusal + crowdsourced)
 * tek uçta topladığından istemci tarafında ayrı market çağrılarına gerek yoktur.
 * Ağ hataları yukarı sızmaz; boş sonuç döner.
 */
class ItemRepository @Inject constructor(
    private val remote: ItemRemoteSource,
) {
    suspend fun search(city: String, name: String): SearchResult =
        runCatching {
            val response = remote.search(city, name)
            SearchResult(
                items = response.body().orEmpty(),
                updatedAt = response.headers()["X-Data-Updated"],
            )
        }.getOrDefault(SearchResult())

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
