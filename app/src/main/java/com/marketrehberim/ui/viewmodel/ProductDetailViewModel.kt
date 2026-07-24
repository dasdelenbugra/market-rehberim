package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.BasketStore
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.HistoryPoint
import com.marketrehberim.data.repository.FavoritesRepository
import com.marketrehberim.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val favoritesRepository: FavoritesRepository,
    private val basketStore: BasketStore,
    private val cityStore: CityStore,
) : ViewModel() {
    private val _history = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val history: StateFlow<List<HistoryPoint>> = _history

    /** Aynı ürünün diğer marketlerdeki fiyatları, ucuzdan pahalıya. */
    private val _comparison = MutableStateFlow<List<Item>>(emptyList())
    val comparison: StateFlow<List<Item>> = _comparison

    fun loadHistory(item: Item) {
        viewModelScope.launch {
            _history.value = itemRepository.history(item.from, item.name)
        }
    }

    /**
     * Ürün adıyla yeniden arayıp marketleri yan yana koyar. Detaya argüman olarak
     * tek bir Item geldiği için karşılaştırma burada kuruluyor; sonuç tek satıra
     * düşerse ekranda bölüm gösterilmez.
     */
    fun loadComparison(item: Item) {
        viewModelScope.launch {
            _comparison.value = itemRepository
                .search(cityStore.cityKey, item.name)
                .sortedBy { it.priceValue }
        }
    }

    fun isFavorite(item: Item): Flow<Boolean> = favoritesRepository.isFavorite(item)

    fun setFavorite(item: Item, makeFavorite: Boolean) {
        viewModelScope.launch { favoritesRepository.toggle(item, makeFavorite) }
    }

    /** @return listeye yeni eklendiyse true; zaten varsa false. */
    fun addToBasket(item: Item): Boolean = basketStore.add(item.name)
}
