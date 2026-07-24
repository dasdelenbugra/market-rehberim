package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.CityDto
import com.marketrehberim.data.remote.dto.MarketsDto
import com.marketrehberim.data.repository.FavoritesRepository
import com.marketrehberim.data.repository.ItemRepository
import com.marketrehberim.data.repository.SavingSummary
import com.marketrehberim.data.repository.SavingsRepository
import com.marketrehberim.data.repository.SearchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    favoritesRepository: FavoritesRepository,
    searchHistoryRepository: SearchHistoryRepository,
    savingsRepository: SavingsRepository,
    private val cityStore: CityStore,
) : ViewModel() {
    val favorites: Flow<List<Item>> = favoritesRepository.favorites
    val recentSearches: Flow<List<String>> = searchHistoryRepository.recent

    /** Anasayfanın en üstündeki şerit: uygulamanın vaadi ilk görülen şey olsun. */
    val monthlySavings: Flow<SavingSummary> = savingsRepository.thisMonth

    private val _cities = MutableStateFlow<List<CityDto>>(emptyList())
    val cities: StateFlow<List<CityDto>> = _cities

    private val _cityLabel = MutableStateFlow(cityStore.cityLabel)
    val cityLabel: StateFlow<String> = _cityLabel

    /** Seçili şehirde karşılaştırılabilen marketler (ulusal + yerel). */
    private val _markets = MutableStateFlow(MarketsDto())
    val markets: StateFlow<MarketsDto> = _markets

    init {
        viewModelScope.launch { _cities.value = itemRepository.cities() }
        loadMarkets()
    }

    fun selectCity(city: CityDto) {
        cityStore.set(city.key, city.label)
        _cityLabel.value = city.label
        // Şehir değişti: marketler de o şehre göre yenilensin.
        loadMarkets()
    }

    private fun loadMarkets() {
        viewModelScope.launch { _markets.value = itemRepository.markets(cityStore.cityKey) }
    }
}
