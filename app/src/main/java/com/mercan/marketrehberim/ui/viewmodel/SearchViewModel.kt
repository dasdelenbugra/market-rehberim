package com.mercan.marketrehberim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mercan.marketrehberim.data.repository.ItemRepository
import com.mercan.marketrehberim.ui.state.UIItemState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
) : ViewModel() {
    private val _searchResults = MutableStateFlow<UIItemState>(UIItemState.Loading)
    val searchResults: StateFlow<UIItemState> = _searchResults

    fun fetchItems(name: String) {
        viewModelScope.launch {
            try {
                val items = itemRepository.getItems(name)
                _searchResults.value = UIItemState.Success(items)
            } catch (e: Exception) {
                _searchResults.value = UIItemState.Error(e.message ?: "Unknown error")
            }
        }
    }
}