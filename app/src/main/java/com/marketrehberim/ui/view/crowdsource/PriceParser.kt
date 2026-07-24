package com.marketrehberim.ui.view.crowdsource

import java.util.Locale

/**
 * Raf etiketi OCR metninden fiyatı ayıklar.
 *
 * Etiketlerde genellikle "12,50", "12.50", "129,90 TL" gibi kalıplar bulunur.
 * En büyük eşleşen değer fiyat olarak kabul edilir (raf fiyatı çoğunlukla en
 * belirgin/büyük sayıdır).
 */
object PriceParser {
    private val regex = Regex("""\d{1,4}[.,]\d{2}""")

    /**
     * @return backend'e gönderilebilir biçimde ("12.50") fiyat, yoksa null.
     *   Biçimlendirme cihaz diline bırakılamaz: Türkçe yerelde "%.2f" ondalık
     *   ayırıcı olarak virgül üretir ve sunucudaki sayı çevrimi başarısız olur.
     */
    fun extractPrice(text: String): String? {
        val values = regex.findAll(text)
            .map { it.value.replace(',', '.') }
            .mapNotNull { it.toDoubleOrNull() }
            .toList()
        return values.maxOrNull()?.let { "%.2f".format(Locale.US, it) }
    }
}
