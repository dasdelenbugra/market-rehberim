package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.ProductGroup
import com.marketrehberim.data.repository.HttpException
import com.marketrehberim.data.repository.ItemRepository
import com.marketrehberim.data.repository.SavingsRepository
import com.marketrehberim.data.repository.SearchHistoryRepository
import com.marketrehberim.ui.state.SearchError
import com.marketrehberim.ui.state.UIItemState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
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

    private var rawGroups: List<ProductGroup> = emptyList()
    private var selectedMarket: String? = null

    /**
     * "İlgili ürünler" bölümü açık mı. Varsayılan kapalı: kullanıcının şikâyeti
     * tam da bu ürünlerin asıl sonuçlara karışmasıydı; isteyen açar.
     */
    private var relatedExpanded: Boolean = false
    private var sortOrder: SortOrder = SortOrder.PRICE_ASC
    private var lastQuery: String = ""

    /**
     * Ulusal fiyatların son indekslenme zamanı (ISO 8601) — `X-Data-Updated`
     * başlığından. Kaynak bildirmezse null; "Son güncelleme" satırı gizlenir.
     */
    var lastUpdatedIso: String? = null
        private set

    fun fetchItems(name: String) {
        val query = name.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _searchResults.value = UIItemState.Loading
            lastQuery = query
            searchHistoryRepository.add(query)

            itemRepository.products(cityStore.cityKey, query)
                .onSuccess { result ->
                    rawGroups = result.groups
                    lastUpdatedIso = result.updatedAt
                    selectedMarket = null
                    relatedExpanded = false
                    _markets.value = rawGroups
                        .flatMap { group -> group.offers.map { it.from } }
                        .distinct()
                        .sorted()
                    emitDisplayed()
                }
                .onFailure { error ->
                    // Eski sonuçlar ekranda kalıp hata mesajıyla karışmasın.
                    rawGroups = emptyList()
                    _markets.value = emptyList()
                    lastUpdatedIso = null
                    _searchResults.value = UIItemState.Error(error.toSearchError())
                }
        }
    }

    /** Hata ekranındaki "Tekrar dene" — son sorguyu aynen yeniler. */
    fun retry() {
        if (lastQuery.isNotEmpty()) fetchItems(lastQuery)
    }

    private fun Throwable.toSearchError(): SearchError = when (this) {
        is HttpException -> SearchError.SERVER
        is IOException -> SearchError.NETWORK
        else -> SearchError.UNKNOWN
    }

    fun setMarketFilter(market: String?) {
        if (_searchResults.value is UIItemState.Loading) return
        selectedMarket = market
        emitDisplayed()
    }

    /** "İlgili ürünler (17)" başlığına dokunuldu. */
    fun toggleRelated() {
        if (_searchResults.value is UIItemState.Loading) return
        relatedExpanded = !relatedExpanded
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
        val highest = rawGroups
            .flatMap { it.offers }
            .map { it.priceValue }
            .filter { it != Double.MAX_VALUE }
            .maxOrNull() ?: return

        viewModelScope.launch {
            savingsRepository.record(lastQuery, item, highest)
        }
    }

    /** Ekrandaki ürün grubu sayısı — üstteki özet satırı bunu yazar. */
    val visibleGroupCount: Int
        get() = rawGroups.count { selectedMarket == null || it.offers.any { o -> o.from == selectedMarket } }

    /** Sonuçlardaki en düşük fiyat; filtre uygulanmış haliyle. */
    val cheapestVisiblePrice: Double?
        get() = rawGroups
            .flatMap { it.offers }
            .filter { selectedMarket == null || it.from == selectedMarket }
            .map { it.priceValue }
            .filter { it != Double.MAX_VALUE }
            .minOrNull()

    private fun emitDisplayed() {
        _searchResults.value = UIItemState.Success(
            ResultShaper.shape(rawGroups, selectedMarket, sortOrder, relatedExpanded)
        )
    }
}
