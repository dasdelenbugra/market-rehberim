package com.marketrehberim.ui.viewmodel

import com.marketrehberim.data.remote.dto.ProductGroup
import com.marketrehberim.ui.state.SearchRow
import java.util.Locale

/**
 * Ham arama sonucunu ekranda gösterilecek satırlara dönüştürür: market filtresi,
 * sıralama ve alaka bölümlemesi. ViewModel'den ayrı tutuldu çünkü burada Android
 * bağımlılığı yok — doğrudan birim testle doğrulanabiliyor.
 */
object ResultShaper {

    /** Backend'in "sorgunun asıl hedefi" dediği alaka katmanı. */
    const val RELEVANCE_HEAD = 0

    /**
     * @param relatedExpanded "İlgili ürünler" bölümü açık mı. Kapalıyken başlık
     *   görünür ama altındaki ürünler listeye eklenmez.
     */
    fun shape(
        groups: List<ProductGroup>,
        market: String?,
        order: SortOrder,
        relatedExpanded: Boolean,
    ): List<SearchRow> {
        val filtered = groups.mapNotNull { it.restrictTo(market) }

        val main = filtered.filter { it.relevance == RELEVANCE_HEAD }.sortedBy(order)
        val related = filtered.filter { it.relevance != RELEVANCE_HEAD }.sortedBy(order)

        // Hiç "asıl hedef" yoksa (örn. "kahvaltılık" gibi genel bir sorgu) bölme
        // yapılmaz: aksi halde ana liste boş görünür ve kullanıcı kapalı bir
        // başlığın ardındaki sonuçları hiç görmez.
        if (main.isEmpty()) {
            return related.map { SearchRow.Product(it) }
        }

        val rows = main.mapTo(mutableListOf<SearchRow>()) { SearchRow.Product(it) }
        if (related.isNotEmpty()) {
            rows += SearchRow.RelatedHeader(related.size, relatedExpanded)
            if (relatedExpanded) related.forEach { rows += SearchRow.Product(it) }
        }
        return rows
    }

    /**
     * Grubu tek bir markete indirger. Filtre açıkken grup, o marketin fiyatını
     * göstermeli — grubun genel en ucuzunu değil; aksi halde "sadece A101"
     * seçiliyken ekranda BİM fiyatı yazardı.
     *
     * @return o markette teklif yoksa null (grup listeden düşer)
     */
    private fun ProductGroup.restrictTo(market: String?): ProductGroup? {
        if (market == null) return this
        val mine = offers.filter { it.from == market }
        if (mine.isEmpty()) return null

        val cheapest = mine.minByOrNull { it.priceValue } ?: return null
        return copy(
            bestPrice = cheapest.price,
            bestMarket = cheapest.from,
            maxPrice = (mine.maxByOrNull { it.priceValue } ?: cheapest).price,
            marketCount = 1,
            unitPrice = cheapest.unitPrice,
            unit = cheapest.unit,
            offers = mine,
        )
    }

    private fun List<ProductGroup>.sortedBy(order: SortOrder): List<ProductGroup> =
        when (order) {
            SortOrder.PRICE_ASC -> sortedBy { it.bestPriceValue }
            SortOrder.PRICE_DESC -> sortedByDescending { it.bestPriceValue }
            // Locale.ROOT: Türkçe yerelde "I"/"ı" kuralı A-Z sıralamasını bozar.
            SortOrder.NAME -> sortedBy { it.name.lowercase(Locale.ROOT) }
        }
}
