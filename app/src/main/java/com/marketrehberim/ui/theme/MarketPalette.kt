package com.marketrehberim.ui.theme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.marketrehberim.R

/**
 * Market adı → marka rengi eşlemesi. Çipler, sonuç satırları, sepet dağılımı ve
 * ürün detayındaki karşılaştırma listesi aynı kaynaktan beslenir; böylece kullanıcı
 * rengi bir kez öğrenip her ekranda tanır.
 *
 * Tanınmayan market "yerel/topluluk" sayılır ve marka yeşiliyle gösterilir —
 * crowdsourced veri zaten yerel marketlerden geliyor.
 */
object MarketPalette {

    @ColorRes
    fun colorResFor(market: String?): Int = when (normalize(market)) {
        "a101" -> R.color.market_a101
        "migros" -> R.color.market_migros
        "sok" -> R.color.market_sok
        "carrefour" -> R.color.market_carrefour
        else -> R.color.market_local
    }

    @ColorInt
    fun colorFor(context: Context, market: String?): Int =
        ContextCompat.getColor(context, colorResFor(market))

    /**
     * Türkçe karakterleri ASCII'ye indirger ve bilinen market anahtarını döner.
     * "ŞOK", "Şok Market", "sok" hepsi "sok" olur.
     */
    private fun normalize(market: String?): String {
        val ascii = market.orEmpty()
            .replace('ş', 's').replace('Ş', 'S')
            .replace('ı', 'i').replace('İ', 'I')
            .replace('ğ', 'g').replace('Ğ', 'G')
            .replace('ü', 'u').replace('Ü', 'U')
            .replace('ö', 'o').replace('Ö', 'O')
            .replace('ç', 'c').replace('Ç', 'C')
            .lowercase()

        return KNOWN.firstOrNull { ascii.contains(it) }.orEmpty()
    }

    private val KNOWN = listOf("a101", "migros", "carrefour", "sok")
}
