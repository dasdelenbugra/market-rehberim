package com.marketrehberim.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTest {

    private fun item(price: String) = Item(name = "Süt", price = price, image = "", from = "Migros")

    @Test
    fun `sayisal fiyati cozer`() {
        assertEquals(38.5, item("38.50").priceValue, 0.001)
    }

    /** Çevrilemeyen fiyat sıralamada sona düşsün diye MAX_VALUE olmalı. */
    @Test
    fun `cozulemeyen fiyat MAX_VALUE olur`() {
        assertEquals(Double.MAX_VALUE, item("stokta yok").priceValue, 0.0)
    }

    @Test
    fun `cozulemeyen fiyati oldugu gibi gosterir`() {
        assertEquals("stokta yok ₺", item("stokta yok").formattedPrice)
    }
}
