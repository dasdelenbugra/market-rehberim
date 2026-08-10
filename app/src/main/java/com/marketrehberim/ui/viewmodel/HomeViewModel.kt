package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.location.CityLocator
import com.marketrehberim.data.model.Item
import com.marketrehberim.data.remote.dto.CityDto
import com.marketrehberim.data.remote.dto.MarketsDto
import com.marketrehberim.data.repository.BarcodeLookup
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
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    favoritesRepository: FavoritesRepository,
    searchHistoryRepository: SearchHistoryRepository,
    savingsRepository: SavingsRepository,
    private val cityStore: CityStore,
    private val cityLocator: CityLocator,
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

    /**
     * Okunan barkodu seçili şehirde arar.
     *
     * Ağ/sunucu hatası ayrı tutulur: "barkodu tanımadım" demek, internet
     * yokken yanıltıcı olur.
     */
    suspend fun lookupBarcode(code: String): Result<BarcodeLookup> =
        itemRepository.barcode(cityStore.cityKey, code)

    /**
     * Cihaz konumundan ili tespit edip desteklenen bir şehre eşler. Eşleşme
     * varsa şehri seçer. İzin çağıran tarafta alınmış olmalıdır.
     */
    suspend fun detectCity(): CityDetection {
        // Liste henüz gelmediyse "desteklenmiyor" demek yanıltıcı olur; ayrı durum.
        val available = cities.value
        if (available.isEmpty()) return CityDetection.NoCityList
        val province = cityLocator.currentProvince() ?: return CityDetection.Unavailable
        val match = available.firstOrNull {
            it.label.normalizeTr() == province.normalizeTr() ||
                it.key.normalizeTr() == province.normalizeTr()
        } ?: return CityDetection.Unsupported(province)
        selectCity(match)
        return CityDetection.Selected(match.label)
    }

    private fun String.normalizeTr(): String = trim().lowercase(Locale("tr", "TR"))
}

/** Konumdan şehir tespitinin olası sonuçları. */
sealed interface CityDetection {
    data class Selected(val label: String) : CityDetection
    data class Unsupported(val province: String) : CityDetection
    data object Unavailable : CityDetection

    /** Şehir listesi backend'den hiç gelmedi (ağ hatası vb.). */
    data object NoCityList : CityDetection
}
