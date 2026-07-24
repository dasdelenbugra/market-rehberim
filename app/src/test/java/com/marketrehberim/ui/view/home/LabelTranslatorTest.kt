package com.marketrehberim.ui.view.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LabelTranslatorTest {

    @Test
    fun `bilinen etiketi turkceye cevirir`() {
        assertEquals("muz", LabelTranslator.toTurkishQuery("Banana"))
    }

    @Test
    fun `bosluklari temizler`() {
        assertEquals("süt", LabelTranslator.toTurkishQuery("  Milk  "))
    }

    @Test
    fun `bilinmeyen etiketi kucuk harfle oldugu gibi kullanir`() {
        assertEquals("sushi", LabelTranslator.toTurkishQuery("Sushi"))
    }

    /**
     * Türkçe yerelde `lowercase()` "I" harfini "ı" yapar; tablo anahtarları
     * İngilizce olduğu için eşleşme kaçar. Çeviri cihaz dilinden bağımsız olmalı.
     */
    @Test
    fun `turkce yerelde I harfli etiket de eslesir`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("meyve suyu", LabelTranslator.toTurkishQuery("Juice"))
            assertEquals("ice cream", LabelTranslator.toTurkishQuery("Ice cream"))
        } finally {
            Locale.setDefault(default)
        }
    }
}
