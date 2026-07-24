package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.repository.ItemRepository
import com.marketrehberim.data.repository.SavingsRepository
import com.marketrehberim.data.repository.SearchHistoryRepository
import com.marketrehberim.ui.state.UIItemState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val savingsRepository: SavingsRepository,
    private val cityStore: CityStore,
) : ViewModel() {
    private val _searchResults = MutableStateFlow<UIItemState>(UIItemState.Idle)
    val searchResults: StateFlow<UIItemState> = _searchResults

    /** Sonuçlarda bulunan marketler (filtre çipleri için). */
    private val _markets = MutableStateFlow<List<String>>(emptyList())
    val markets: StateFlow<List<String>> = _markets

    val cityLabel: String get() = cityStore.cityLabel

    private var rawItems: List<Item> = emptyList()
    private var selectedMarket: String? = null
    private var sortOrder: SortOrder = SortOrder.PRICE_ASC
    private var lastQuery: String = ""

    fun fetchItems(name: String) {
        val query = name.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            try {
                _searchResults.value = UIItemState.Loading
                lastQuery = query
                searchHistoryRepository.add(query)
                rawItems = itemRepository.search(cityStore.cityKey, query)
                selectedMarket = null
                _markets.value = rawItems.map { it.from }.distinct().sorted()
                emitDisplayed()
            } catch (e: Exception) {
                _searchResults.value = UIItemState.Error(e.message ?: "Bilinmeyen hata")
            }
        }
    }

    fun setMarketFilter(market: String?) {
        if (_searchResults.value is UIItemState.Loading) return
        selectedMarket = market
        emitDisplayed()
    }

    fun setSortOrder(order: SortOrder) {
        if (_searchResults.value is UIItemState.Loading) return
        sortOrder = order
        emitDisplayed()
    }

    val currentSort: SortOrder get() = sortOrder

    /**
     * Kullanıcı bir sonucu açtığında kazancı kaydeder: seçilen fiyat ile aynı
     * aramadaki en yüksek fiyatın farkı. Filtreden değil ham sonuçtan bakılır —
     * "market filtresi açıkken kazanç" diye bir şey yok.
     */
    fun recordSelection(item: Item) {
        val highest = rawItems
            .map { it.priceValue }
            .filter { it != Double.MAX_VALUE }
            .maxOrNull() ?: return

        viewModelScope.launch {
            savingsRepository.record(lastQuery, item, highest)
        }
    }

    private fun emitDisplayed() {
        _searchResults.value =
            UIItemState.Success(ResultShaper.shape(rawItems, selectedMarket, sortOrder))
    }
}
