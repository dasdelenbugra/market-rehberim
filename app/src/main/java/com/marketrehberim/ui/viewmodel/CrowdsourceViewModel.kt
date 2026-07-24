package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.remote.dto.PriceSubmission
import com.marketrehberim.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrowdsourceViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val cityStore: CityStore,
) : ViewModel() {
    val cityLabel: String get() = cityStore.cityLabel

    private val _markets = MutableStateFlow<List<String>>(emptyList())
    val markets: StateFlow<List<String>> = _markets

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting

    /** Gönderim sonucu olayları (true = başarılı). */
    private val _events = MutableSharedFlow<Boolean>()
    val events: SharedFlow<Boolean> = _events

    fun loadMarkets() {
        viewModelScope.launch {
            val m = itemRepository.markets(cityStore.cityKey)
            // Yerel marketler öne (crowdsourced fiyat asıl onlar için anlamlı)
            _markets.value = (m.local + m.national).distinct()
        }
    }

    fun submit(market: String, name: String, price: String) {
        viewModelScope.launch {
            _submitting.value = true
            val ok = itemRepository.submitPrice(
                PriceSubmission(
                    city = cityStore.cityKey,
                    market = market,
                    name = name.trim(),
                    price = price,
                )
            )
            _submitting.value = false
            _events.emit(ok)
        }
    }
}
