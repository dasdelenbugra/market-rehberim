package com.marketrehberim.ui.viewmodel

import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.ProductGroup
import com.marketrehberim.ui.state.SearchRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ResultShaperTest {

    private fun offer(from: String, price: String, name: String = "x") =
        Item(name = name, price = price, image = "", from = from)

    /**
     * @param relevance 0 = sorgunun asıl hedefi, 1 = yalnızca ilgili ürün.
     */
    private fun group(
        name: String,
        relevance: Int = ResultShaper.RELEVANCE_HEAD,
        vararg offers: Item,
    ): ProductGroup {
        val sorted = offers.sortedBy { it.priceValue }
        return ProductGroup(
            name = name,
            bestPrice = sorted.first().price,
            bestMarket = sorted.first().from,
            maxPrice = sorted.last().price,
            marketCount = sorted.size,
            relevance = relevance,
            offers = sorted.map { it.copy(name = name) },
        )
    }

    private val sample = listOf(
        group("Süt 1L", offers = arrayOf(offer("Migros", "38.50"), offer("A101", "35.00"))),
        group("Ayran", offers = arrayOf(offer("A101", "12.00"))),
        group("Peynir", offers = arrayOf(offer("Migros", "129.90"))),
    )

    private fun names(rows: List<SearchRow>) =
        rows.filterIsInstance<SearchRow.Product>().map { it.group.name }

    private fun shape(
        groups: List<ProductGroup>,
        market: String? = null,
        order: SortOrder = SortOrder.PRICE_ASC,
        relatedExpanded: Boolean = false,
    ) = ResultShaper.shape(groups, market, order, relatedExpanded)

    @Test
    fun `market filtresi yoksa tum gruplar doner`() {
        assertEquals(3, names(shape(sample)).size)
    }

    @Test
    fun `market filtresi sadece o markete ait gruplari birakir`() {
        assertEquals(listOf("Süt 1L", "Peynir"), names(shape(sample, market = "Migros")))
    }

    /**
     * Filtre açıkken grup, seçilen marketin fiyatını göstermeli. Aksi halde
     * "sadece Migros" seçiliyken satırda A101'in daha ucuz fiyatı yazardı.
     */
    @Test
    fun `market filtresi grubun fiyatini o markete gore yeniden hesaplar`() {
        val rows = shape(sample, market = "Migros")
        val sut = rows.filterIsInstance<SearchRow.Product>().first { it.group.name == "Süt 1L" }
        assertEquals("38.50", sut.group.bestPrice)
        assertEquals("Migros", sut.group.bestMarket)
        assertEquals(1, sut.group.marketCount)
    }

    @Test
    fun `fiyata gore artan siralar`() {
        assertEquals(listOf("Ayran", "Süt 1L", "Peynir"), names(shape(sample)))
    }

    @Test
    fun `fiyata gore azalan siralar`() {
        assertEquals(
            listOf("Peynir", "Süt 1L", "Ayran"),
            names(shape(sample, order = SortOrder.PRICE_DESC)),
        )
    }

    /** Fiyatı çevrilemeyen kayıt listenin sonuna düşmeli, ortada kaybolmamalı. */
    @Test
    fun `okunamayan fiyat artan siralamada sona gider`() {
        val withBad = sample + group("Bilinmeyen", offers = arrayOf(offer("ŞOK", "fiyat yok")))
        assertEquals("Bilinmeyen", names(shape(withBad)).last())
    }

    /**
     * Türkçe yerelde "I".lowercase() → "ı" olur ve A-Z sırası kayar; sıralama
     * cihaz dilinden bağımsız olmalı.
     */
    @Test
    fun `isim siralamasi turkce yerelde de ayni kalir`() {
        val default = Locale.getDefault()
        val groups = listOf(
            group("Islak Mendil", offers = arrayOf(offer("A101", "10.00"))),
            group("Ayran", offers = arrayOf(offer("A101", "12.00"))),
            group("Zeytin", offers = arrayOf(offer("A101", "50.00"))),
        )
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals(
                listOf("Ayran", "Islak Mendil", "Zeytin"),
                names(shape(groups, order = SortOrder.NAME)),
            )
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `bos liste bos doner`() {
        assertEquals(emptyList<SearchRow>(), shape(emptyList(), market = "Migros"))
    }

    // --- Alaka bölümlemesi --------------------------------------------------

    private val mixed = listOf(
        group("Yerli Muz 1 Kg", 0, offer("BİM", "79.00")),
        group("Muz Kremalı Gofret", 1, offer("A101", "8.00")),
        group("Muz Aromalı Süt", 1, offer("ŞOK", "15.50")),
    )

    /**
     * Asıl şikâyet buydu: 8 ₺'lik gofret, fiyata göre sıralandığı için 79 ₺'lik
     * muzun üstüne çıkıyor ve "EN UCUZ" rozetini alıyordu.
     */
    @Test
    fun `ilgili urunler ana listenin altina iner`() {
        assertEquals(listOf("Yerli Muz 1 Kg"), names(shape(mixed)))
    }

    @Test
    fun `ilgili urunler basligi sayiyi tasir`() {
        val header = shape(mixed).filterIsInstance<SearchRow.RelatedHeader>().single()
        assertEquals(2, header.count)
        assertTrue(!header.expanded)
    }

    @Test
    fun `baslik acilinca ilgili urunler listeye eklenir`() {
        assertEquals(
            listOf("Yerli Muz 1 Kg", "Muz Kremalı Gofret", "Muz Aromalı Süt"),
            names(shape(mixed, relatedExpanded = true)),
        )
    }

    /**
     * Hiç "asıl hedef" yoksa bölme yapılmamalı: aksi halde ana liste boş görünür
     * ve kullanıcı kapalı bir başlığın ardındaki tüm sonuçları hiç göremez.
     */
    @Test
    fun `sadece ilgili urun varsa bolumleme yapilmaz`() {
        val onlyRelated = mixed.filter { it.relevance != ResultShaper.RELEVANCE_HEAD }
        val rows = shape(onlyRelated)
        assertTrue(rows.none { it is SearchRow.RelatedHeader })
        assertEquals(listOf("Muz Kremalı Gofret", "Muz Aromalı Süt"), names(rows))
    }

    @Test
    fun `filtre ile eslesmeyen grup tamamen duser`() {
        assertEquals(emptyList<String>(), names(shape(mixed, market = "CarrefourSA")))
    }

    // --- Birim fiyat öncelikli sıralama ------------------------------------

    /**
     * Kullanıcının bildirdiği somut hata: 350 gr havuç 32,90 ₺ (94 ₺/kg) paket
     * fiyatıyla 1 kg 35 ₺'lik havucun (35 ₺/kg) üstüne çıkıp "EN UCUZ" oluyordu.
     */
    private val havuc = listOf(
        group("Havuç Paket 350 Gr", offers = arrayOf(offer("A101", "32.90")))
            .copy(unitPrice = "94.00", unit = "kg"),
        group("Havuç 1 Kg", offers = arrayOf(offer("BİM", "35.00")))
            .copy(unitPrice = "35.00", unit = "kg"),
        group("Mini Havuç 1 Adet", offers = arrayOf(offer("CarrefourSA", "229.90"))),
    )

    @Test
    fun `birim fiyati dusuk olan pakete gore ucuz gorunse de one gecer`() {
        assertEquals(
            listOf("Havuç 1 Kg", "Havuç Paket 350 Gr", "Mini Havuç 1 Adet"),
            names(shape(havuc)),
        )
    }

    /** Birim fiyatı olmayan grup paket fiyatıyla yarışır; listeden düşmez. */
    @Test
    fun `birim fiyatsiz grup paket fiyatiyla siralanir`() {
        val rows = names(shape(havuc, order = SortOrder.PRICE_DESC))
        assertEquals("Havuç 1 Kg", rows.last())
    }

    /**
     * Özet "en ucuz" fiyatı, rozeti alan grubun **paket** fiyatını söylemeli:
     * kazanan birim fiyata göre seçilir ama ekrandaki sayıyla aynı olmalı.
     */
    @Test
    fun `ozet fiyati rozeti alan grubun paket fiyatidir`() {
        assertEquals(35.00, ResultShaper.summaryPrice(havuc, market = null)!!, 0.001)
    }

    @Test
    fun `ozet fiyati market filtresine uyar`() {
        assertEquals(32.90, ResultShaper.summaryPrice(havuc, market = "A101")!!, 0.001)
    }

    @Test
    fun `ozet ana bolum bos ise ilgili urunlerden hesaplanir`() {
        val onlyRelated = mixed.filter { it.relevance != ResultShaper.RELEVANCE_HEAD }
        assertEquals(8.00, ResultShaper.summaryPrice(onlyRelated, market = null)!!, 0.001)
    }
}
