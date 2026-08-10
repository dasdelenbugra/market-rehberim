package com.marketrehberim.data.remote

import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.BarcodeResponse
import com.marketrehberim.data.remote.dto.BasketRequest
import com.marketrehberim.data.remote.dto.BasketResponse
import com.marketrehberim.data.remote.dto.CityDto
import com.marketrehberim.data.remote.dto.HistoryPoint
import com.marketrehberim.data.remote.dto.MarketsDto
import com.marketrehberim.data.remote.dto.PriceSubmission
import com.marketrehberim.data.remote.dto.ProductGroup
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ItemRemoteSource {
    /**
     * Şehir + ürün için birleşik arama (ulusal + crowdsourced).
     *
     * `Response<...>` döner çünkü gövdenin yanında `X-Data-Updated` başlığını da
     * okuyoruz — ulusal fiyatların son indekslenme zamanı ("Son güncelleme" rozeti).
     */
    @GET("search/{city}/{itemName}")
    suspend fun search(
        @Path("city") city: String,
        @Path("itemName") itemName: String,
    ): Response<List<Item>>

    /**
     * Ürüne göre gruplanmış arama sonucu — arama ekranının kullandığı uç.
     * `search` düz liste döndürmeye devam ediyor: ürün detayındaki market
     * karşılaştırması ve sepet optimizasyonu ona bağlı.
     */
    @GET("products/{city}/{itemName}")
    suspend fun products(
        @Path("city") city: String,
        @Path("itemName") itemName: String,
    ): Response<List<ProductGroup>>

    /**
     * Barkoddan ürün + fiyatlar.
     *
     * `Response<...>` şart: 404 "barkodu tanımadım", 200 + boş `items` ise
     * "ürünü tanıdım ama bu şehirde fiyatı yok" demek. İkisi kullanıcıya farklı
     * şey söylüyor, gövdeye bakmak ayırt etmeye yetmez.
     */
    @GET("barcode/{city}/{code}")
    suspend fun barcode(
        @Path("city") city: String,
        @Path("code") code: String,
    ): Response<BarcodeResponse>

    @GET("cities")
    suspend fun cities(): List<CityDto>

    @GET("markets/{city}")
    suspend fun markets(@Path("city") city: String): MarketsDto

    /** Crowdsourced fiyat gönderimi (raf etiketi OCR sonucu). */
    @POST("prices")
    suspend fun submitPrice(@Body body: PriceSubmission): Response<Unit>

    /** Sepet optimizasyonu. */
    @POST("basket/optimize")
    suspend fun optimizeBasket(@Body body: BasketRequest): BasketResponse

    /** Fiyat geçmişi (grafik verisi). */
    @GET("history/{market}/{itemName}")
    suspend fun history(
        @Path("market") market: String,
        @Path("itemName") itemName: String,
    ): List<HistoryPoint>
}
