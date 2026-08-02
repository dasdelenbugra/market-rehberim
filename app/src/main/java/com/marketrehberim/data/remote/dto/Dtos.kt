package com.marketrehberim.data.remote.dto

import com.marketrehberim.data.model.Item

/** GET /cities */
data class CityDto(
    val key: String,
    val label: String,
)

/** GET /markets/<city> */
data class MarketsDto(
    val national: List<String> = emptyList(),
    val local: List<String> = emptyList(),
)

/** POST /prices — crowdsourced fiyat gönderimi (OCR sonucu) */
data class PriceSubmission(
    val city: String,
    val market: String,
    val name: String,
    val price: String,
    val image: String? = null,
)

/** POST /basket/optimize isteği */
data class BasketRequest(
    val city: String,
    val items: List<String>,
)

/** POST /basket/optimize yanıtı */
data class BasketResponse(
    val byMarket: List<MarketBasket> = emptyList(),
    val optimalSplit: OptimalSplit = OptimalSplit(),
)

data class MarketBasket(
    val market: String,
    val total: Double,
    val foundCount: Int,
    val totalCount: Int,
    val complete: Boolean,
    val items: List<BasketLine> = emptyList(),
)

data class BasketLine(
    val name: String,
    val price: String?,
)

data class OptimalSplit(
    val items: List<OptimalLine> = emptyList(),
    val total: Double = 0.0,
)

data class OptimalLine(
    val name: String,
    val market: String?,
    val price: String?,
)

/** GET /history/<market>/<itemName> */
data class HistoryPoint(
    val price: String,
    val date: String,
)

/**
 * GET /products/<city>/<name> — aynı ürünü tek satırda toplayan grup.
 *
 * Arama sonucu iki eksende karışık geliyordu: hangi ürün (muz / muzlu gofret) ve
 * hangi market (aynı muz beş markette). Bu tip birinci ekseni satır, ikincisini
 * satırın içeriği yapar.
 *
 * @param relevance 0 = sorgunun asıl hedefi ("Yerli Muz"), 1 = yalnızca ilgili
 *   ("Muz Aromalı Süt"). Liste bu ayrımla iki bölüme ayrılır.
 * @param offers marketlerin teklifleri, ucuzdan pahalıya.
 */
data class ProductGroup(
    val name: String,
    val image: String = "",
    val unitPrice: String? = null,
    val unit: String? = null,
    val bestPrice: String,
    val bestMarket: String,
    val maxPrice: String,
    val marketCount: Int,
    val relevance: Int,
    val offers: List<Item> = emptyList(),
) {
    val bestPriceValue: Double get() = bestPrice.toDoubleOrNull() ?: Double.MAX_VALUE

    /**
     * Sıralama ve "EN UCUZ" rozeti için karşılaştırma anahtarı: birim fiyat
     * (₺/kg, ₺/L) varsa o, yoksa paket fiyatı.
     *
     * Paket fiyatıyla karşılaştırınca 350 gr havuç (32,90 ₺ → 94 ₺/kg) rozeti
     * 1 kg havuçtan (35 ₺ → 35 ₺/kg) çalıyordu; kullanıcı "en ucuz" diye
     * kilosu üç kat pahalı ürünü görüyordu.
     */
    val comparablePriceValue: Double
        get() = unitPrice?.toDoubleOrNull() ?: bestPriceValue

    /** Detaya geçerken taşınan kayıt: grubun en ucuz teklifi. */
    val cheapestOffer: Item?
        get() = offers.minByOrNull { it.priceValue }
}

/** Item -> crowdsourced gönderim gövdesi dönüşümü için yardımcı. */
fun Item.toSubmission(city: String): PriceSubmission =
    PriceSubmission(city = city, market = from, name = name, price = price, image = image)
