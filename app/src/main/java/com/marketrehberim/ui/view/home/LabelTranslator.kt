package com.marketrehberim.ui.view.home

import java.util.Locale

/**
 * ML Kit görüntü etiketleyicisi İngilizce etiketler üretir (ör. "Banana").
 * Market araması Türkçe olduğundan, sık karşılaşılan gıda/market nesneleri
 * Türkçe karşılıklarına eşlenir. Tabloda bulunmayan etiketler küçük harfe
 * çevrilerek olduğu gibi kullanılır.
 */
object LabelTranslator {
    private val map = mapOf(
        "banana" to "muz",
        "apple" to "elma",
        "orange" to "portakal",
        "strawberry" to "çilek",
        "grape" to "üzüm",
        "lemon" to "limon",
        "peach" to "şeftali",
        "pear" to "armut",
        "watermelon" to "karpuz",
        "tomato" to "domates",
        "cucumber" to "salatalık",
        "potato" to "patates",
        "carrot" to "havuç",
        "onion" to "soğan",
        "pepper" to "biber",
        "bread" to "ekmek",
        "milk" to "süt",
        "cheese" to "peynir",
        "egg" to "yumurta",
        "water" to "su",
        "juice" to "meyve suyu",
        "chocolate" to "çikolata",
        "cookie" to "bisküvi",
        "coffee" to "kahve",
        "tea" to "çay",
    )

    /**
     * Küçültme Locale.ROOT ile yapılır: cihaz dili Türkçeyken `lowercase()`
     * "I" harfini "ı"ya çevirir ("Ice cream" → "ıce cream"), tablodaki İngilizce
     * anahtarlar tutmaz ve arama İngilizce etiketle yapılır.
     */
    fun toTurkishQuery(label: String): String {
        val key = label.trim().lowercase(Locale.ROOT)
        return map[key] ?: key
    }
}
