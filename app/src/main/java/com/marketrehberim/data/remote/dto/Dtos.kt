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

/** Item -> crowdsourced gönderim gövdesi dönüşümü için yardımcı. */
fun Item.toSubmission(city: String): PriceSubmission =
    PriceSubmission(city = city, market = from, name = name, price = price, image = image)
