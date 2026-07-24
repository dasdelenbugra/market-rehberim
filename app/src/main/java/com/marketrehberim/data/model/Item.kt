package com.marketrehberim.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Item(
    val name: String,
    val price: String,
    val image: String,
    val from: String,
) : Parcelable {
    /** Fiyatın sayısal karşılığı; çevrilemezse çok büyük değer (sona sıralanır). */
    val priceValue: Double
        get() = price.toDoubleOrNull() ?: Double.MAX_VALUE

    /** Ekranda gösterilecek biçim: iki ondalık + para birimi. */
    val formattedPrice: String
        get() = price.toDoubleOrNull()?.let { "%.2f ₺".format(it) } ?: "$price ₺"
}
