package com.marketrehberim.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alışveriş listesi. Uygulama ömrü boyunca tek örnek olduğu için hem sepet
 * ekranı hem alt gezinme rozeti hem de ürün detayındaki "sepete ekle" aynı
 * listeyi görür — liste artık tek bir Fragment'in içinde hapis değil.
 *
 * Kalıcı değil: liste oturumluk, fiyatlar zaten her aramada tazeleniyor.
 */
@Singleton
class BasketStore @Inject constructor() {
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items

    /** @return ürün gerçekten eklendiyse true; zaten listedeyse false. */
    fun add(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (_items.value.any { it.equals(trimmed, ignoreCase = true) }) return false
        _items.value = _items.value + trimmed
        return true
    }

    fun remove(name: String) {
        _items.value = _items.value - name
    }
}
