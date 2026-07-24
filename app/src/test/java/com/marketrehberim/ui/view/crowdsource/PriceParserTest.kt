package com.marketrehberim.ui.view.crowdsource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PriceParserTest {

    @Test
    fun `virgullu fiyati okur`() {
        assertEquals("12.50", PriceParser.extractPrice("SÜT 1L\n12,50 TL"))
    }

    @Test
    fun `noktali fiyati okur`() {
        assertEquals("12.50", PriceParser.extractPrice("SÜT 1L 12.50"))
    }

    /** Raf etiketinde birden çok sayı varsa raf fiyatı en büyük olandır. */
    @Test
    fun `birden fazla sayidan en buyugunu secer`() {
        assertEquals("129.90", PriceParser.extractPrice("İndirim 19,90 yerine 129,90 TL"))
    }

    @Test
    fun `sayi yoksa null doner`() {
        assertNull(PriceParser.extractPrice("KAMPANYA"))
    }

    @Test
    fun `ondalikli olmayan sayiyi fiyat saymaz`() {
        assertNull(PriceParser.extractPrice("Raf no 12"))
    }

    /**
     * Çıktı backend'e gidiyor: cihaz dili Türkçe olsa bile ondalık ayırıcı nokta
     * kalmalı, yoksa sunucudaki sayı çevrimi başarısız olur.
     */
    @Test
    fun `turkce yerelde de nokta ile bicimlendirir`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("12.50", PriceParser.extractPrice("12,50 TL"))
        } finally {
            Locale.setDefault(default)
        }
    }
}
