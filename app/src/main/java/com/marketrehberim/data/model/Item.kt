package com.marketrehberim.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * @param unitPrice birim başına fiyat ("112.50"), backend ürün adından türetir.
 *   Miktar okunamayan üründe `null` — bkz. backend `app/units.py`. Eski
 *   sürümlerin yanıtında bu alan hiç bulunmaz, Gson da `null` bırakır.
 * @param unit `unitPrice`'ın birimi: "L" ya da "kg".
 */
@Parcelize
data class Item(
    val name: String,
    val price: String,
    val image: String,
    val from: String,
    val unitPrice: String? = null,
    val unit: String? = null,
) : Parcelable {
    /** Fiyatın sayısal karşılığı; çevrilemezse çok büyük değer (sona sıralanır). */
    val priceValue: Double
        get() = price.toDoubleOrNull() ?: Double.MAX_VALUE

    /** Ekranda gösterilecek biçim: iki ondalık + para birimi. */
    val formattedPrice: String
        get() = price.toDoubleOrNull()?.let { "%.2f ₺".format(it) } ?: "$price ₺"

    /**
     * "112,50 ₺/L" — farklı boydaki paketleri kıyaslanabilir kılan satır.
     * İkisinden biri yoksa `null`: yarım bilgiyle "₺/null" yazmaktansa gizlenir.
     */
    val formattedUnitPrice: String?
        get() {
            val value = unitPrice?.toDoubleOrNull() ?: return null
            val suffix = unit ?: return null
            return "%.2f ₺/%s".format(value, suffix)
        }
}
