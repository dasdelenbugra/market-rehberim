package com.marketrehberim.ui.viewmodel

import com.marketrehberim.data.model.Item
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ResultShaperTest {

    private fun item(name: String, price: String, from: String) =
        Item(name = name, price = price, image = "", from = from)

    private val sample = listOf(
        item("Süt 1L", "38.50", "Migros"),
        item("Ayran", "12.00", "A101"),
        item("Peynir", "129.90", "Migros"),
    )

    @Test
    fun `market filtresi yoksa tum sonuclar doner`() {
        val result = ResultShaper.shape(sample, market = null, order = SortOrder.PRICE_ASC)
        assertEquals(3, result.size)
    }

    @Test
    fun `market filtresi sadece o marketi birakir`() {
        val result = ResultShaper.shape(sample, market = "Migros", order = SortOrder.PRICE_ASC)
        assertEquals(listOf("Süt 1L", "Peynir"), result.map { it.name })
    }

    @Test
    fun `fiyata gore artan siralar`() {
        val result = ResultShaper.shape(sample, market = null, order = SortOrder.PRICE_ASC)
        assertEquals(listOf("Ayran", "Süt 1L", "Peynir"), result.map { it.name })
    }

    @Test
    fun `fiyata gore azalan siralar`() {
        val result = ResultShaper.shape(sample, market = null, order = SortOrder.PRICE_DESC)
        assertEquals(listOf("Peynir", "Süt 1L", "Ayran"), result.map { it.name })
    }

    /** Fiyatı çevrilemeyen kayıt listenin sonuna düşmeli, ortada kaybolmamalı. */
    @Test
    fun `okunamayan fiyat artan siralamada sona gider`() {
        val withBad = sample + item("Bilinmeyen", "fiyat yok", "ŞOK")
        val result = ResultShaper.shape(withBad, market = null, order = SortOrder.PRICE_ASC)
        assertEquals("Bilinmeyen", result.last().name)
    }

    /**
     * Türkçe yerelde "I".lowercase() → "ı" olur ve A-Z sırası kayar; sıralama
     * cihaz dilinden bağımsız olmalı.
     */
    @Test
    fun `isim siralamasi turkce yerelde de ayni kalir`() {
        val default = Locale.getDefault()
        val items = listOf(
            item("Islak Mendil", "10.00", "A101"),
            item("Ayran", "12.00", "A101"),
            item("Zeytin", "50.00", "A101"),
        )
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val result = ResultShaper.shape(items, market = null, order = SortOrder.NAME)
            assertEquals(listOf("Ayran", "Islak Mendil", "Zeytin"), result.map { it.name })
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `bos liste bos doner`() {
        val result = ResultShaper.shape(emptyList(), market = "Migros", order = SortOrder.NAME)
        assertEquals(emptyList<Item>(), result)
    }
}
