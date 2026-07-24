package com.marketrehberim.ui.viewmodel

import com.marketrehberim.data.model.Item
import java.util.Locale

/**
 * Ham arama sonucunu ekranda gösterilecek listeye dönüştürür: market filtresi +
 * sıralama. ViewModel'den ayrı tutuldu çünkü burada Android bağımlılığı yok —
 * doğrudan birim testle doğrulanabiliyor.
 */
object ResultShaper {
    fun shape(items: List<Item>, market: String?, order: SortOrder): List<Item> {
        val filtered = if (market == null) items else items.filter { it.from == market }
        return when (order) {
            SortOrder.PRICE_ASC -> filtered.sortedBy { it.priceValue }
            SortOrder.PRICE_DESC -> filtered.sortedByDescending { it.priceValue }
            // Locale.ROOT: Türkçe yerelde "I"/"ı" kuralı A-Z sıralamasını bozar.
            SortOrder.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }
}
