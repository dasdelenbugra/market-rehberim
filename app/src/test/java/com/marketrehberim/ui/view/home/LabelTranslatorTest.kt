package com.marketrehberim.ui.view.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LabelTranslatorTest {

    @Test
    fun `sozlukte olan urun etiketini turkceye cevirir`() {
        assertEquals(LabelMatch.Product("ekmek"), LabelTranslator.match("Bread"))
        assertEquals(LabelMatch.Product("kuskus"), LabelTranslator.match("Couscous"))
    }

    @Test
    fun `bosluklari temizler`() {
        assertEquals(LabelMatch.Product("kahve"), LabelTranslator.match("  Coffee  "))
    }

    /**
     * Modelin sözlüğünde ürün adı yok; elma "Fruit", domates "Vegetable",
     * süt "Food" olarak dönüyor. Bunları aramak kaynakta alakasız sonuç
     * veriyordu ("meyve" → meyve suyu), o yüzden ayrı bir durum.
     */
    @Test
    fun `ust kategori etiketi urun sayilmaz`() {
        assertEquals(LabelMatch.GenericFood, LabelTranslator.match("Food"))
        assertEquals(LabelMatch.GenericFood, LabelTranslator.match("Fruit"))
        assertEquals(LabelMatch.GenericFood, LabelTranslator.match("Vegetable"))
    }

    @Test
    fun `gida disi etiket eslesmez`() {
        assertEquals(LabelMatch.None, LabelTranslator.match("Bicycle"))
        assertEquals(LabelMatch.None, LabelTranslator.match("Shetland sheepdog"))
    }

    /**
     * Modelin üretemeyeceği etiketler tabloya girmemeli: çevirisi varmış gibi
     * durur, testi geçer, sahada hiç tetiklenmez. Sözlükte "Banana" yok.
     */
    @Test
    fun `sozlukte olmayan meyve etiketi tabloda yer almaz`() {
        assertEquals(LabelMatch.None, LabelTranslator.match("Banana"))
        assertEquals(LabelMatch.None, LabelTranslator.match("Apple"))
        assertEquals(LabelMatch.None, LabelTranslator.match("Milk"))
    }

    /**
     * Türkçe yerelde `lowercase()` "I" harfini "ı" yapar; tablo anahtarları
     * İngilizce olduğu için eşleşme kaçar. Çeviri cihaz dilinden bağımsız olmalı.
     */
    @Test
    fun `turkce yerelde de eslesir`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(LabelMatch.Product("meyve suyu"), LabelTranslator.match("Juice"))
            assertEquals(LabelMatch.GenericFood, LabelTranslator.match("Picnic"))
        } finally {
            Locale.setDefault(default)
        }
    }
}
