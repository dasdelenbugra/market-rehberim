package com.marketrehberim.ui.state

import com.marketrehberim.data.remote.dto.ProductGroup

/**
 * Arama listesindeki bir satır.
 *
 * Liste artık homojen değil: sorgunun asıl hedefi olan ürünler üstte, yalnızca
 * ilgili olanlar ("muz" araması için muzlu gofret) katlanabilir bir başlığın
 * altında durur. Bölüm başlığı da bir satır olduğu için tek RecyclerView yeter.
 */
sealed interface SearchRow {
    data class Product(val group: ProductGroup) : SearchRow

    /**
     * "İlgili ürünler (17)" başlığı.
     *
     * @param expanded açıksa altındaki ürünler listeye eklenmiştir.
     */
    data class RelatedHeader(val count: Int, val expanded: Boolean) : SearchRow
}
