package com.marketrehberim.data.repository

import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.ItemRemoteSource
import com.marketrehberim.data.remote.dto.BasketRequest
import com.marketrehberim.data.remote.dto.BasketResponse
import com.marketrehberim.data.remote.dto.CityDto
import com.marketrehberim.data.remote.dto.HistoryPoint
import com.marketrehberim.data.remote.dto.MarketsDto
import com.marketrehberim.data.remote.dto.PriceSubmission
import com.marketrehberim.data.remote.dto.ProductGroup
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

/** Gruplanmış arama sonucu + ulusal fiyatların son güncellenme zamanı. */
data class ProductResult(
    val groups: List<ProductGroup> = emptyList(),
    val updatedAt: String? = null,
)

/** Backend 2xx dışında yanıt verdi. Kodu, ağ hatasından ayırmak için taşınır. */
class HttpException(val code: Int) : Exception("HTTP $code")

/**
 * Uzak veri kaynağına erişim. Backend birleşik aramayı (ulusal + crowdsourced)
 * tek uçta topladığından istemci tarafında ayrı market çağrılarına gerek yoktur.
 *
 * Arama dışındaki uçlar hatayı yutup boş değer döner — anasayfadaki market/şehir
 * listeleri yardımcı bilgi, yüklenememeleri ekranı bloklamamalı. **Arama** ise
 * `Result` döndürür: eskiden o da yutuluyordu ve internet yokken kullanıcı
 * "sonuç bulunamadı" görüp ürünün olmadığını sanıyordu.
 */
class ItemRepository @Inject constructor(
    private val remote: ItemRemoteSource,
) {
    suspend fun search(city: String, name: String): Result<SearchResult> =
        runCatching {
            val response = remote.search(city, name)
            // Retrofit 4xx/5xx'te istisna atmaz; sunucu hatası sessizce boş
            // listeye dönüşmesin diye burada açıkça hataya çeviriyoruz.
            if (!response.isSuccessful) {
                throw HttpException(response.code())
            }
            SearchResult(
                items = response.body().orEmpty(),
                updatedAt = response.headers()["X-Data-Updated"],
            )
        }

    /**
     * Arama ekranının kullandığı gruplu sonuç. Hata işleme [search] ile aynı
     * gerekçeye tabi: internet yokken "sonuç bulunamadı" göstermek yanlış.
     */
    suspend fun products(city: String, name: String): Result<ProductResult> =
        runCatching {
            val response = remote.products(city, name)
            if (!response.isSuccessful) {
                throw HttpException(response.code())
            }
            ProductResult(
                groups = response.body().orEmpty(),
                updatedAt = response.headers()["X-Data-Updated"],
            )
        }

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
