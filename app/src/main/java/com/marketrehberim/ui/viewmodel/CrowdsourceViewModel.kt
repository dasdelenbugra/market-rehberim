package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.remote.dto.PriceSubmission
import com.marketrehberim.data.repository.ItemRepository
import com.marketrehberim.data.repository.SubmitRejected
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

    /** Gönderim sonucu olayları. */
    private val _events = MutableSharedFlow<SubmitOutcome>()
    val events: SharedFlow<SubmitOutcome> = _events

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
            val outcome = itemRepository.submitPrice(
                PriceSubmission(
                    city = cityStore.cityKey,
                    market = market,
                    name = name.trim(),
                    price = price,
                )
            ).fold(
                onSuccess = { SubmitOutcome.Success },
                onFailure = { error ->
                    // Sunucunun gerekçesi varsa onu göster: "Fiyat ... aralığında
                    // olmalı (okunan: 0.00 ₺)" kullanıcıya ne yapacağını söyler,
                    // "gönderilemedi" söylemez.
                    val reason = (error as? SubmitRejected)?.reason
                    if (reason != null) SubmitOutcome.Rejected(reason)
                    else SubmitOutcome.Failed
                },
            )
            _submitting.value = false
            _events.emit(outcome)
        }
    }
}

/** Fiyat gönderiminin sonucu. */
sealed interface SubmitOutcome {
    data object Success : SubmitOutcome

    /** Sunucu gövdeyi doğrulayıp reddetti; [reason] kullanıcıya gösterilebilir. */
    data class Rejected(val reason: String) : SubmitOutcome

    /** Ağ hatası ya da gerekçesi okunamayan yanıt. */
    data object Failed : SubmitOutcome
}
