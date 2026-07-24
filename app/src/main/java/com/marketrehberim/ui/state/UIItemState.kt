package com.marketrehberim.ui.state

import com.marketrehberim.data.model.Item

sealed class UIItemState {
    data object Idle : UIItemState()
    data object Loading : UIItemState()
    data class Success(val items: List<Item>) : UIItemState()
    data class Error(val message: String) : UIItemState()
}