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
import org.json.JSONObject
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
 * Gruplanmış arama sonucu + ulusal fiyatların son güncellenme zamanı.
 *
 * @param suggestion `X-Search-Suggestion` başlığından gelen "bunu mu demek
 *   istediniz" önerisi. Sunucu yalnız sonuç boşken ve yeterince emin olduğunda
 *   gönderir; markette gerçekten bulunmayan bir ürün için bilerek `null` kalır.
 */
data class ProductResult(
    val groups: List<ProductGroup> = emptyList(),
    val updatedAt: String? = null,
    val suggestion: String? = null,
)

/**
 * Barkod sorgusunun olası sonuçları.
 *
 * Ağ/sunucu hatası buraya girmez; o `Result`'ın hata tarafında taşınır.
 */
sealed interface BarcodeLookup {
    /** Ürün çözümlendi ve şehirde fiyatı var. */
    data class Found(val product: String, val items: List<Item>) : BarcodeLookup

    /** Barkod tanındı ama seçili şehirde bu ürünün fiyatı yok. */
    data class NoPrices(val product: String) : BarcodeLookup

    /** Barkod, ürün veritabanında hiç yok (Open Food Facts kapsamı tam değil). */
    data object UnknownBarcode : BarcodeLookup
}

/** Backend 2xx dışında yanıt verdi. Kodu, ağ hatasından ayırmak için taşınır. */
class HttpException(val code: Int) : Exception("HTTP $code")

/**
 * Fiyat gönderimi sunucu tarafından reddedildi.
 *
 * @param reason sunucunun Türkçe gerekçesi; okunamadıysa `null` ve arayüz
 *   genel mesaja düşer.
 */
class SubmitRejected(val reason: String?) : Exception(reason ?: "reddedildi")

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
                suggestion = response.headers()["X-Search-Suggestion"],
            )
        }

    /**
     * Barkodu ürüne çevirir ve şehirdeki fiyatlarını getirir.
     *
     * Üç sonucu ayırmak, kullanıcıya doğru şeyi söyleyebilmek için: barkod
     * veritabanında yok / ürün bulundu ama şehirde satılmıyor / bulundu.
     * Hepsini "sonuç yok"a indirmek, önceki kamera akışının hatasıydı.
     */
    suspend fun barcode(city: String, code: String): Result<BarcodeLookup> =
        runCatching {
            val response = remote.barcode(city, code)
            when {
                response.code() == 404 -> BarcodeLookup.UnknownBarcode
                !response.isSuccessful -> throw HttpException(response.code())
                else -> {
                    val body = response.body() ?: throw HttpException(response.code())
                    if (body.items.isEmpty()) BarcodeLookup.NoPrices(body.product)
                    else BarcodeLookup.Found(body.product, body.items)
                }
            }
        }

    suspend fun cities(): List<CityDto> =
        runCatching { remote.cities() }.getOrDefault(emptyList())

    suspend fun markets(city: String): MarketsDto =
        runCatching { remote.markets(city) }.getOrDefault(MarketsDto())

    suspend fun history(market: String, name: String): List<HistoryPoint> =
        runCatching { remote.history(market, name) }.getOrDefault(emptyList())

    suspend fun optimizeBasket(city: String, items: List<String>): BasketResponse? =
        runCatching { remote.optimizeBasket(BasketRequest(city, items)) }.getOrNull()

    /**
     * Crowdsourced fiyat gönderir.
     *
     * Eskiden yalnız `Boolean` dönüyordu ve kullanıcı her başarısızlıkta aynı
     * genel mesajı görüyordu. Backend artık gerekçeyi Türkçe söylüyor
     * ("Fiyat 0.10 - 20000 ₺ aralığında olmalı (okunan: 0.00 ₺)") — bu, çoğu
     * durumda OCR'ın etiketi okuyamadığı anlamına gelir ve kullanıcının fiyatı
     * elle düzeltmesi gerektiğini anlatır. Mesajı yutmak o yardımı çöpe atıyordu.
     *
     * @return başarıda `Unit`; reddedilirse [SubmitRejected] taşıyan hata.
     */
    suspend fun submitPrice(submission: PriceSubmission): Result<Unit> =
        runCatching {
            val response = remote.submitPrice(submission)
            if (!response.isSuccessful) {
                throw SubmitRejected(serverReason(response.errorBody()?.string()))
            }
        }

    /**
     * Hata gövdesinden `{"error": "..."}` alanını çıkarır.
     *
     * Gövde beklenen biçimde değilse (vekil sunucunun HTML hata sayfası, boş
     * yanıt) `null` döner ve çağıran genel mesaja düşer — burada ham HTML
     * göstermek kullanıcı için hiçbir şey ifade etmez.
     */
    private fun serverReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            JSONObject(body).optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
