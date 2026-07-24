package com.marketrehberim.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BasketStoreTest {

    @Test
    fun `urun ekler`() {
        val store = BasketStore()
        assertTrue(store.add("süt"))
        assertEquals(listOf("süt"), store.items.value)
    }

    @Test
    fun `bastaki ve sondaki bosluklari kirpar`() {
        val store = BasketStore()
        store.add("  ekmek  ")
        assertEquals(listOf("ekmek"), store.items.value)
    }

    @Test
    fun `bos girdiyi reddeder`() {
        val store = BasketStore()
        assertFalse(store.add("   "))
        assertTrue(store.items.value.isEmpty())
    }

    /** Aynı ürünü iki kez eklemek sepeti şişirmemeli; büyük/küçük harf de aynı ürün. */
    @Test
    fun `ayni urunu harf durumundan bagimsiz tekrar eklemez`() {
        val store = BasketStore()
        assertTrue(store.add("Süt"))
        assertFalse(store.add("süt"))
        assertEquals(1, store.items.value.size)
    }

    @Test
    fun `urun cikarir`() {
        val store = BasketStore()
        store.add("süt")
        store.add("ekmek")
        store.remove("süt")
        assertEquals(listOf("ekmek"), store.items.value)
    }
}
