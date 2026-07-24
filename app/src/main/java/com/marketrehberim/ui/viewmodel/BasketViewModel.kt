package com.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marketrehberim.data.local.BasketStore
import com.marketrehberim.data.local.CityStore
import com.marketrehberim.data.remote.dto.BasketResponse
import com.marketrehberim.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BasketViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val basketStore: BasketStore,
    private val cityStore: CityStore,
) : ViewModel() {
    /** Liste uygulama düzeyinde tutuluyor; alt gezinme rozeti de bunu okur. */
    val items: StateFlow<List<String>> = basketStore.items

    private val _result = MutableStateFlow<BasketResponse?>(null)
    val result: StateFlow<BasketResponse?> = _result

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun addItem(name: String) {
        basketStore.add(name)
    }

    fun removeItem(name: String) {
        basketStore.remove(name)
        // Liste boşaldıysa eldeki sonuç artık hiçbir şeyi anlatmıyor.
        if (basketStore.items.value.isEmpty()) _result.value = null
    }

    fun optimize() {
        if (items.value.isEmpty()) return
        viewModelScope.launch {
            _loading.value = true
            _result.value = itemRepository.optimizeBasket(cityStore.cityKey, items.value)
            _loading.value = false
        }
    }
}
